//! Runner de pipeline de processamento de uma reuniao. Retomavel: cada etapa
//! le o estado do banco, faz seu trabalho e avanca. Se o app cair, a proxima
//! execucao continua da etapa registrada sem repetir as anteriores.
//!
//! Fase 2: transcricao e diarizacao implementadas. Identificacao de vozes e
//! resumo sao marcadores que apenas avancam o estado ate serem construidos.

use std::path::PathBuf;
use std::sync::Arc;

use tauri::{AppHandle, Emitter};

use crate::db::meetings::{self, Stage};
use crate::db::settings;
use crate::db::{segments, Db};
use crate::diarize::{self, Diarizer};
use crate::error::{AppError, AppResult};
use crate::models::{catalog, ModelManager};
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
    models: Arc<ModelManager>,
    app: AppHandle,
}

impl Pipeline {
    pub fn new(
        db: Db,
        paths: AppPaths,
        models: Arc<ModelManager>,
        app: AppHandle,
    ) -> Self {
        Self {
            db,
            paths,
            models,
            app,
        }
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
                    self.emit(&meeting_id, Stage::Diarizing, 0.0, "carregando modelos de voz");
                    if let Err(e) = self.diarize(&meeting) {
                        // Diarizacao falha nao invalida a transcricao: segue sem
                        // separacao de vozes e registra o motivo.
                        tracing::warn!(meeting = %meeting_id, "diarizacao pulada: {e}");
                        self.emit(
                            &meeting_id,
                            Stage::Diarizing,
                            1.0,
                            &format!("separacao de vozes indisponivel: {e}"),
                        );
                    }
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

    fn diarize(&self, meeting: &meetings::Meeting) -> AppResult<()> {
        let audio = meeting
            .audio_path
            .as_ref()
            .ok_or_else(|| AppError::Other("reuniao sem audio".into()))?;
        let samples = transcribe::load_mono_16k(&PathBuf::from(audio))?;
        if samples.is_empty() {
            return Err(AppError::Audio("audio vazio".into()));
        }

        let seg_model = self
            .models
            .resolve_file("sherpa-segmentation-pyannote", Some("model.onnx"))?;
        let emb_model = self
            .models
            .resolve_file("sherpa-speaker-embedding-campplus", None)?;

        let n = settings::load(&self.db, &self.paths)
            .map(|s| s.participant_count as i32)
            .unwrap_or(3);

        let mut diarizer = Diarizer::load(&seg_model, &emb_model, Some(n))?;

        let mid = meeting.id.clone();
        let app = self.app.clone();
        let voice = diarizer.run(samples, move |p| {
            let _ = app.emit(
                "pipeline://progress",
                PipelineProgress {
                    meeting_id: mid.clone(),
                    stage: Stage::Diarizing,
                    progress: p,
                    message: "separando vozes".to_string(),
                },
            );
        })?;

        let transcript: Vec<(f64, f64)> = segments::list(&self.db, &meeting.id)?
            .iter()
            .map(|s| (s.start_secs, s.end_secs))
            .collect();
        let clusters = diarize::assign_clusters(&transcript, &voice, 0.2);
        segments::set_clusters(&self.db, &meeting.id, &clusters)?;

        let distintos: std::collections::HashSet<_> =
            clusters.iter().flatten().collect();
        tracing::info!(
            meeting = %meeting.id,
            vozes = distintos.len(),
            spans = voice.len(),
            "diarizacao concluida"
        );
        Ok(())
    }
}
