//! Runner de pipeline de processamento de uma reuniao. Retomavel: cada etapa
//! le o estado do banco, faz seu trabalho e avanca. Se o app cair, a proxima
//! execucao continua da etapa registrada sem repetir as anteriores.
//!
//! Fase 2: implementada a transcricao. Diarizacao, identificacao e resumo sao
//! marcadores que apenas avancam o estado ate serem construidos.

use std::path::PathBuf;
use std::sync::Arc;

use tauri::{AppHandle, Emitter};

use crate::db::meetings::{self, Stage};
use crate::db::{segments, Db};
use crate::error::{AppError, AppResult};
use crate::models::catalog;
use crate::paths::AppPaths;
use crate::transcribe::{self, Transcriber};

#[derive(Clone, serde::Serialize)]
pub struct PipelineProgress {
    pub meeting_id: String,
    pub stage: Stage,
    pub progress: f32,
    pub message: String,
}

pub struct Pipeline {
    db: Db,
    paths: AppPaths,
    app: AppHandle,
}

impl Pipeline {
    pub fn new(db: Db, paths: AppPaths, app: AppHandle) -> Self {
        Self { db, paths, app }
    }

    fn emit(&self, meeting_id: &str, stage: Stage, progress: f32, message: &str) {
        let _ = self.app.emit(
            "pipeline://progress",
            PipelineProgress {
                meeting_id: meeting_id.to_string(),
                stage,
                progress,
                message: message.to_string(),
            },
        );
    }

    fn set_stage(&self, id: &str, stage: Stage, err: Option<&str>) -> AppResult<()> {
        meetings::set_stage(&self.db, id, stage, err)
    }

    /// Processa a reuniao a partir da etapa atual ate `completed` ou `failed`.
    pub fn run(self: Arc<Self>, meeting_id: String) -> AppResult<()> {
        let mut meeting = meetings::get(&self.db, &meeting_id)?;

        loop {
            match meeting.stage {
                Stage::Finalizing => {
                    self.set_stage(&meeting_id, Stage::Transcribing, None)?;
                }
                Stage::Transcribing => {
                    self.emit(&meeting_id, Stage::Transcribing, 0.0, "carregando modelo");
                    if let Err(e) = self.transcribe(&meeting) {
                        self.set_stage(&meeting_id, Stage::Failed, Some(&e.to_string()))?;
                        return Err(e);
                    }
                    self.set_stage(&meeting_id, Stage::Diarizing, None)?;
                }
                Stage::Diarizing => {
                    // TODO Fase 2: Sherpa-ONNX. Por ora avanca sem alterar segmentos.
                    self.emit(&meeting_id, Stage::Diarizing, 1.0, "etapa ainda nao implementada");
                    self.set_stage(&meeting_id, Stage::Identifying, None)?;
                }
                Stage::Identifying => {
                    self.emit(&meeting_id, Stage::Identifying, 1.0, "etapa ainda nao implementada");
                    self.set_stage(&meeting_id, Stage::Summarizing, None)?;
                }
                Stage::Summarizing => {
                    // TODO Fase 2: llama.cpp + Qwen3.
                    self.emit(&meeting_id, Stage::Summarizing, 1.0, "etapa ainda nao implementada");
                    self.set_stage(&meeting_id, Stage::Completed, None)?;
                }
                Stage::Completed | Stage::Failed | Stage::Cancelled => {
                    self.emit(&meeting_id, meeting.stage, 1.0, "concluido");
                    return Ok(());
                }
                Stage::Recording => {
                    return Err(AppError::Other(
                        "a reuniao ainda esta gravando".into(),
                    ));
                }
            }
            meeting = meetings::get(&self.db, &meeting_id)?;
        }
    }

    fn whisper_model_path(&self) -> AppResult<PathBuf> {
        // Usa o large-v3-turbo (padrao de seguranca do plano). Configuravel depois.
        let def = catalog::find("whisper-large-v3-turbo-q5_0")
            .ok_or_else(|| AppError::Model("catalogo sem whisper padrao".into()))?;
        Ok(self.paths.models_dir.join(def.filename))
    }

    fn transcribe(&self, meeting: &meetings::Meeting) -> AppResult<()> {
        let audio = meeting
            .audio_path
            .as_ref()
            .ok_or_else(|| AppError::Other("reuniao sem audio".into()))?;
        let audio_path = PathBuf::from(audio);

        let model_path = self.whisper_model_path()?;
        let transcriber = Transcriber::load(&model_path)?;

        let samples = transcribe::load_mono_16k(&audio_path)?;
        if samples.is_empty() {
            return Err(AppError::Audio("audio vazio".into()));
        }

        let mid = meeting.id.clone();
        let app = self.app.clone();
        let raw = transcriber.run(&samples, move |p| {
            let _ = app.emit(
                "pipeline://progress",
                PipelineProgress {
                    meeting_id: mid.clone(),
                    stage: Stage::Transcribing,
                    progress: p,
                    message: "transcrevendo".to_string(),
                },
            );
        })?;

        let new_segments: Vec<segments::NewSegment> = raw
            .into_iter()
            .map(|s| segments::NewSegment {
                start_secs: s.start_secs,
                end_secs: s.end_secs,
                text: s.text,
            })
            .collect();

        segments::replace_all(&self.db, &meeting.id, &new_segments)?;
        tracing::info!(
            meeting = %meeting.id,
            segmentos = new_segments.len(),
            "transcricao concluida"
        );
        Ok(())
    }
}
