//! Runner de pipeline de processamento de uma reuniao. Retomavel: cada etapa
//! le o estado do banco, faz seu trabalho e avanca. Se o app cair, a proxima
//! execucao continua da etapa registrada sem repetir as anteriores.
//!
//! Fases locais: transcricao, diarizacao, identificacao de vozes e resumo.

use std::path::PathBuf;
use std::sync::Arc;

use tauri::{AppHandle, Emitter};

use crate::audio::wav::WavWriter;
use crate::audio::TARGET_SAMPLE_RATE;
use crate::db::meetings::{self, Stage};
use crate::db::settings;
use crate::db::{segments, speakers, summary, Db};
use crate::diarize;
use crate::error::{AppError, AppResult};
use crate::models::{catalog, ModelManager};
use crate::paths::AppPaths;
use crate::speaker;
use crate::summarize::{LabeledLine, Summarizer};
use crate::transcribe::{self, Transcriber};

#[cfg(target_os = "windows")]
const LLAMA_CLI_NAME: &str = "llama-cli.exe";
#[cfg(target_os = "android")]
const LLAMA_CLI_NAME: &str = "llama-cli";
#[cfg(not(any(target_os = "windows", target_os = "android")))]
const LLAMA_CLI_NAME: &str = "llama-cli";

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
    pub fn new(db: Db, paths: AppPaths, models: Arc<ModelManager>, app: AppHandle) -> Self {
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
                    self.emit(
                        &meeting_id,
                        Stage::Diarizing,
                        0.0,
                        "carregando modelos de voz",
                    );
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
                    self.emit(
                        &meeting_id,
                        Stage::Identifying,
                        0.0,
                        "comparando vozes cadastradas",
                    );
                    if let Err(e) = self.identify(&meeting) {
                        tracing::warn!(meeting = %meeting_id, "identificacao de vozes pulada: {e}");
                        self.emit(
                            &meeting_id,
                            Stage::Identifying,
                            1.0,
                            &format!("identificação indisponível: {e}"),
                        );
                    }
                    self.set_stage(&meeting_id, Stage::Summarizing, None)?;
                }
                Stage::Summarizing => {
                    self.emit(
                        &meeting_id,
                        Stage::Summarizing,
                        0.0,
                        "carregando modelo de resumo",
                    );
                    if let Err(e) = self.summarize(&meeting) {
                        let message = format!("ata nao gerada: {e}");
                        self.set_stage(&meeting_id, Stage::Failed, Some(&message))?;
                        tracing::error!(meeting = %meeting_id, "{message}");
                        self.emit(&meeting_id, Stage::Summarizing, 1.0, &message);
                        return Err(e);
                    }
                    self.set_stage(&meeting_id, Stage::Completed, None)?;
                }
                Stage::Completed | Stage::Failed | Stage::Cancelled => {
                    self.emit(&meeting_id, meeting.stage, 1.0, "concluido");
                    return Ok(());
                }
                Stage::Recording => {
                    return Err(AppError::Other("a reuniao ainda esta gravando".into()));
                }
            }
            meeting = meetings::get(&self.db, &meeting_id)?;
        }
    }

    fn whisper_model_path(&self) -> AppResult<PathBuf> {
        // Modelo escolhido nas configuracoes. Vazio ou invalido -> recomendado
        // pela RAM. Se nem esse estiver baixado, tenta qualquer whisper presente.
        let chosen = settings::load(&self.db, &self.paths)
            .map(|s| s.whisper_model)
            .unwrap_or_default();

        let candidates: Vec<&str> = {
            let mut v = Vec::new();
            if !chosen.is_empty() {
                v.push(chosen.as_str());
            }
            v.push(crate::models::recommended_whisper_id());
            for m in catalog::CATALOG
                .iter()
                .filter(|m| m.kind == catalog::ModelKind::Whisper && m.profile.is_some())
            {
                v.push(m.id);
            }
            v
        };

        for id in candidates {
            if let Some(def) = catalog::find(id) {
                let p = self.paths.models_dir.join(def.filename);
                if p.exists() {
                    return Ok(p);
                }
            }
        }
        Err(AppError::Model(
            "nenhum modelo de transcricao baixado — escolha um na aba Modelos".into(),
        ))
    }

    fn transcribe(&self, meeting: &meetings::Meeting) -> AppResult<()> {
        let audio = meeting
            .audio_path
            .as_ref()
            .ok_or_else(|| AppError::Other("reuniao sem audio".into()))?;
        let audio_path = PathBuf::from(audio);

        let model_path = self.whisper_model_path()?;
        let transcriber = Transcriber::load(&model_path)?;
        self.emit(
            &meeting.id,
            Stage::Transcribing,
            0.02,
            "modelo carregado — lendo áudio",
        );

        let samples = transcribe::load_mono_16k(&audio_path)?;
        if samples.is_empty() {
            return Err(AppError::Audio("audio vazio".into()));
        }
        self.emit(
            &meeting.id,
            Stage::Transcribing,
            0.05,
            "áudio carregado — iniciando análise",
        );

        let mid = meeting.id.clone();
        let app = self.app.clone();
        let raw = transcriber.run(&samples, move |p| {
            let _ = app.emit(
                "pipeline://progress",
                PipelineProgress {
                    meeting_id: mid.clone(),
                    stage: Stage::Transcribing,
                    // A preparação ocupa os primeiros 5%; a inferência fica
                    // em 5..95%, deixando 100% para a gravação no banco.
                    progress: 0.05 + p.clamp(0.0, 1.0) * 0.90,
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

        self.emit(
            &meeting.id,
            Stage::Transcribing,
            0.95,
            "salvando transcrição",
        );
        segments::replace_all(&self.db, &meeting.id, &new_segments)?;
        self.emit(
            &meeting.id,
            Stage::Transcribing,
            1.0,
            "transcrição concluída",
        );
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
        let exe = self.models.resolve_file(
            "sherpa-onnx-bin",
            Some("bin/sherpa-onnx-offline-speaker-diarization.exe"),
        )?;

        // O exe le WAV; escreve um temporario mono 16 kHz a partir do audio.
        let tmp_wav = std::env::temp_dir().join(format!("atalocal-diarize-{}.wav", meeting.id));
        write_wav_mono16k(&tmp_wav, &samples)?;

        self.emit(&meeting.id, Stage::Diarizing, 0.3, "separando vozes");
        let voice = diarize::run(&exe, &seg_model, &emb_model, &tmp_wav, None);
        let _ = std::fs::remove_file(&tmp_wav);
        let voice = voice?;

        let transcript: Vec<(f64, f64)> = segments::list(&self.db, &meeting.id)?
            .iter()
            .map(|s| (s.start_secs, s.end_secs))
            .collect();
        let clusters = diarize::assign_clusters(&transcript, &voice, 0.2);
        segments::set_clusters(&self.db, &meeting.id, &clusters)?;

        let distintos: std::collections::HashSet<_> = clusters.iter().flatten().collect();
        tracing::info!(
            meeting = %meeting.id,
            vozes = distintos.len(),
            spans = voice.len(),
            "diarizacao concluida"
        );
        Ok(())
    }

    fn identify(&self, meeting: &meetings::Meeting) -> AppResult<()> {
        let all_segments = segments::list(&self.db, &meeting.id)?;
        let clusters: Vec<i64> = all_segments
            .iter()
            .filter_map(|segment| segment.cluster)
            .collect::<std::collections::BTreeSet<_>>()
            .into_iter()
            .collect();
        if clusters.is_empty() {
            return Ok(());
        }

        let profiles = speakers::list_embeddings(&self.db)?;
        if profiles.is_empty() {
            self.emit(
                &meeting.id,
                Stage::Identifying,
                1.0,
                "nenhuma voz cadastrada",
            );
            return Ok(());
        }

        let audio = meeting
            .audio_path
            .as_ref()
            .ok_or_else(|| AppError::Other("reuniao sem audio".into()))?;
        let samples = transcribe::load_mono_16k(&PathBuf::from(audio))?;
        let (embedding_model, library) = speaker::model_paths(&self.models)?;
        let helper = speaker::helper_executable()?;
        let mut matches = Vec::with_capacity(clusters.len());

        for (index, cluster) in clusters.iter().enumerate() {
            let ranges: Vec<(f64, f64)> = all_segments
                .iter()
                .filter(|segment| segment.cluster == Some(*cluster))
                .map(|segment| (segment.start_secs, segment.end_secs))
                .collect();
            let cluster_samples = speaker::samples_for_ranges(&samples, &ranges);
            let embedding = match speaker::extract_isolated(
                &helper,
                &library,
                &embedding_model,
                &cluster_samples,
            ) {
                Ok(embedding) => embedding,
                Err(error) => {
                    tracing::debug!(
                        meeting = %meeting.id,
                        cluster,
                        "cluster sem amostra suficiente para identificação: {error}"
                    );
                    matches.push((*cluster, None, 0.0));
                    continue;
                }
            };
            let best = profiles
                .iter()
                .filter_map(|profile| {
                    speaker::cosine_similarity(&embedding, &profile.embedding)
                        .map(|score| (profile, score))
                })
                .max_by(|(_, left), (_, right)| left.total_cmp(right));
            let (speaker_id, confidence) = match best {
                Some((profile, score)) if score >= 0.60 => (Some(profile.id.clone()), score),
                _ => (None, 0.0),
            };
            matches.push((*cluster, speaker_id, confidence));
            self.emit(
                &meeting.id,
                Stage::Identifying,
                (index + 1) as f32 / clusters.len() as f32,
                "comparando vozes",
            );
        }

        segments::set_speaker_matches(&self.db, &meeting.id, &matches)?;
        tracing::info!(meeting = %meeting.id, clusters = clusters.len(), "identificacao de vozes concluida");
        Ok(())
    }

    fn summarize(&self, meeting: &meetings::Meeting) -> AppResult<()> {
        let segs = segments::list(&self.db, &meeting.id)?;
        if segs.is_empty() {
            return Err(AppError::Other("sem transcricao para resumir".into()));
        }

        let exe = self
            .models
            .resolve_file("llama-cpp-bin", Some(LLAMA_CLI_NAME))?;
        let model = self
            .models
            .resolve_file("qwen3-4b-instruct-q4_k_m", None)
            .or_else(|_| {
                let def = catalog::find("qwen3-4b-instruct-q4_k_m").unwrap();
                let p = self.paths.models_dir.join(def.filename);
                if p.exists() {
                    Ok(p)
                } else {
                    Err(AppError::Model("modelo de resumo ausente".into()))
                }
            })?;

        let lines: Vec<LabeledLine> = segs
            .iter()
            .map(|s| LabeledLine {
                start_secs: s.start_secs,
                speaker: s
                    .speaker_name
                    .clone()
                    .or_else(|| s.cluster.map(|c| format!("Voz {}", c + 1)))
                    .unwrap_or_else(|| "Voz não identificada".into()),
                text: s.text.clone(),
            })
            .collect();

        let summarizer = Summarizer::new(&exe, &model)?;
        let mid = meeting.id.clone();
        let app = self.app.clone();
        let minutes = summarizer.run(&lines, move |p| {
            let _ = app.emit(
                "pipeline://progress",
                PipelineProgress {
                    meeting_id: mid.clone(),
                    stage: Stage::Summarizing,
                    progress: p,
                    message: "gerando a ata".to_string(),
                },
            );
        })?;

        summary::replace(&self.db, &meeting.id, &minutes)?;
        tracing::info!(
            meeting = %meeting.id,
            decisoes = minutes.decisions.len(),
            tarefas = minutes.action_items.len(),
            "ata gerada"
        );
        Ok(())
    }
}

/// Escreve amostras mono f32 como WAV PCM16 16 kHz (entrada do exe do sherpa).
fn write_wav_mono16k(path: &std::path::Path, samples: &[f32]) -> AppResult<()> {
    let mut w = WavWriter::create(path, 1, TARGET_SAMPLE_RATE, 16)?;
    let pcm: Vec<i16> = samples
        .iter()
        .map(|s| (s.clamp(-1.0, 1.0) * i16::MAX as f32) as i16)
        .collect();
    w.write_i16(&pcm)?;
    w.finalize()
}
