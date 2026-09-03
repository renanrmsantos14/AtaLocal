//! Sessao de gravacao ativa: no maximo uma por vez. Liga o recorder a uma
//! `meeting` no banco, cuida da finalizacao dos arquivos e da conversao para
//! FLAC na etapa `finalizing`.

use std::path::PathBuf;

use parking_lot::Mutex;
use serde::Serialize;

use crate::audio::flac;
use crate::audio::recorder::{self, RecorderHandle, RecordingPaths, RecordingSession};
use crate::db::meetings::{self, Stage};
use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::paths::AppPaths;

struct Active {
    meeting_id: String,
    session: RecordingSession,
    handle: RecorderHandle,
    original_wav: PathBuf,
    processing_wav: PathBuf,
}

pub struct SessionManager {
    db: Db,
    paths: AppPaths,
    active: Mutex<Option<Active>>,
}

#[derive(Debug, Serialize)]
pub struct RecordingState {
    pub meeting_id: Option<String>,
    pub recording: bool,
    pub level: f32,
    pub peak: f32,
    pub duration_secs: f64,
    /// "ok" | "baixo" | "saturado" | "sem_sinal"
    pub signal: &'static str,
    pub error: Option<String>,
}

impl SessionManager {
    pub fn new(db: Db, paths: AppPaths) -> Self {
        Self {
            db,
            paths,
            active: Mutex::new(None),
        }
    }

    #[allow(dead_code)] // usado pela verificacao de encerramento (Fase 4)
    pub fn is_recording(&self) -> bool {
        self.active.lock().is_some()
    }

    pub fn start(&self, title: &str, device: Option<String>) -> AppResult<meetings::Meeting> {
        let mut guard = self.active.lock();
        if guard.is_some() {
            return Err(AppError::Other("ja existe uma gravacao em andamento".into()));
        }

        let meeting = meetings::create(&self.db, title)?;
        let dir = &self.paths.recordings_dir;
        let original_wav = dir.join(format!("{}-original.wav", meeting.id));
        let processing_wav = dir.join(format!("{}-16k.wav", meeting.id));

        let session = recorder::start(
            device.as_deref(),
            RecordingPaths {
                original: original_wav.clone(),
                processing: processing_wav.clone(),
            },
        )
        .inspect_err(|e| {
            let _ = meetings::set_stage(
                &self.db,
                &meeting.id,
                Stage::Failed,
                Some(&format!("falha ao abrir o microfone: {e}")),
            );
        })?;

        let handle = session.handle();
        *guard = Some(Active {
            meeting_id: meeting.id.clone(),
            session,
            handle,
            original_wav,
            processing_wav,
        });
        Ok(meeting)
    }

    /// Para a gravacao, finaliza os WAV, converte para FLAC e move a meeting
    /// para `finalizing`. Retorna o id da meeting encerrada.
    pub fn stop(&self) -> AppResult<String> {
        let active = self
            .active
            .lock()
            .take()
            .ok_or_else(|| AppError::Other("nenhuma gravacao ativa".into()))?;

        let status = active.handle.status();
        let duration = status.duration_secs;

        if let Err(e) = active.session.finish() {
            meetings::set_stage(&self.db, &active.meeting_id, Stage::Failed, Some(&e.to_string()))?;
            return Err(e);
        }

        // Converte para FLAC (sem perda). Se falhar, mantem o WAV como fallback.
        let flac_dir = &self.paths.recordings_dir;
        let original_flac = flac_dir.join(format!("{}-original.flac", active.meeting_id));
        let processing_flac = flac_dir.join(format!("{}-16k.flac", active.meeting_id));

        let processing_final = match flac::wav_to_flac(&active.processing_wav, &processing_flac) {
            Ok(_) => {
                let _ = std::fs::remove_file(&active.processing_wav);
                processing_flac
            }
            Err(e) => {
                tracing::warn!(meeting = %active.meeting_id, "flac da copia falhou: {e}; mantendo wav");
                active.processing_wav.clone()
            }
        };
        match flac::wav_to_flac(&active.original_wav, &original_flac) {
            Ok(_) => {
                let _ = std::fs::remove_file(&active.original_wav);
            }
            Err(e) => {
                tracing::warn!(meeting = %active.meeting_id, "flac do original falhou: {e}; mantendo wav");
            }
        }

        meetings::finish_recording(
            &self.db,
            &active.meeting_id,
            duration,
            &processing_final.to_string_lossy(),
        )?;
        Ok(active.meeting_id)
    }

    pub fn cancel(&self) -> AppResult<()> {
        let active = match self.active.lock().take() {
            Some(a) => a,
            None => return Ok(()),
        };
        let _ = active.session.finish();
        for p in [&active.original_wav, &active.processing_wav] {
            let _ = std::fs::remove_file(p);
        }
        meetings::set_stage(&self.db, &active.meeting_id, Stage::Cancelled, None)?;
        Ok(())
    }

    pub fn state(&self) -> RecordingState {
        let guard = self.active.lock();
        match guard.as_ref() {
            None => RecordingState {
                meeting_id: None,
                recording: false,
                level: 0.0,
                peak: 0.0,
                duration_secs: 0.0,
                signal: "sem_sinal",
                error: None,
            },
            Some(a) => {
                let s = a.handle.status();
                let signal = if s.error.is_some() {
                    "sem_sinal"
                } else if s.peak >= 0.98 {
                    "saturado"
                } else if s.duration_secs > 2.0 && s.level < 0.005 {
                    "baixo"
                } else {
                    "ok"
                };
                RecordingState {
                    meeting_id: Some(a.meeting_id.clone()),
                    recording: s.error.is_none(),
                    level: s.level,
                    peak: s.peak,
                    duration_secs: s.duration_secs,
                    signal,
                    error: s.error,
                }
            }
        }
    }
}
