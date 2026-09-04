mod audio;
mod db;
mod diagnostics;
mod diarize;
mod error;
mod models;
mod paths;
mod pipeline;
mod session;
mod speaker;
mod summarize;
mod transcribe;

/// Superficie minima para testes de integracao. Nao usar em runtime.
pub mod testing {
    use std::path::Path;

    use crate::db::Db;
    use crate::diagnostics::{self, SystemDiagnostics};
    use crate::error::AppResult;
    use crate::models::catalog::CATALOG;
    use crate::paths::AppPaths;

    fn paths_at(dir: &Path) -> AppPaths {
        AppPaths {
            data_dir: dir.to_path_buf(),
            models_dir: dir.join("models"),
            recordings_dir: dir.join("recordings"),
            logs_dir: dir.join("logs"),
            db_path: dir.join("atalocal.db"),
        }
    }

    pub fn run_diagnostics_at(dir: &Path) -> AppResult<SystemDiagnostics> {
        diagnostics::run(&paths_at(dir))
    }

    pub fn open_db_and_list_tables(dir: &Path) -> AppResult<Vec<String>> {
        std::fs::create_dir_all(dir)?;
        let db = Db::open(&paths_at(dir))?;
        db.with(|conn| {
            let mut stmt =
                conn.prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")?;
            let rows = stmt
                .query_map([], |r| r.get::<_, String>(0))?
                .collect::<Result<Vec<_>, _>>()?;
            Ok(rows)
        })
    }

    /// (id, url, tamanho declarado em bytes) de cada modelo do catalogo.
    pub fn model_catalog() -> Vec<(&'static str, &'static str, u64)> {
        CATALOG
            .iter()
            .map(|m| (m.id, m.url, m.size_bytes))
            .collect()
    }

    /// Utilitarios de audio expostos para teste (sem abrir o microfone).
    pub mod audio {
        use std::path::Path;

        pub use crate::audio::resample::Downsampler;
        pub use crate::audio::wav::WavWriter;
        use crate::error::AppResult;

        pub fn wav_create(
            path: &Path,
            channels: u16,
            sample_rate: u32,
            bits: u16,
        ) -> AppResult<WavWriter> {
            WavWriter::create(path, channels, sample_rate, bits)
        }

        pub fn downsampler(src_rate: u32, src_channels: u16, dst_rate: u32) -> Downsampler {
            Downsampler::new(src_rate, src_channels, dst_rate)
        }

        pub fn wav_to_flac(src: &Path, dst: &Path) -> AppResult<u64> {
            crate::audio::flac::wav_to_flac(src, dst)
        }
    }

    /// Resumo exposto para teste de integracao (roda o llama-cli).
    pub mod summarize {
        use std::path::Path;

        use crate::error::AppResult;
        use crate::summarize::{LabeledLine, MeetingMinutes, Summarizer};

        pub fn run(
            exe: &Path,
            model: &Path,
            lines: &[(f64, String, String)],
        ) -> AppResult<MeetingMinutes> {
            let labeled: Vec<LabeledLine> = lines
                .iter()
                .map(|(t, s, x)| LabeledLine {
                    start_secs: *t,
                    speaker: s.clone(),
                    text: x.clone(),
                })
                .collect();
            Summarizer::new(exe, model)?.run(&labeled, |_| {})
        }
    }

    /// Transcricao exposta para teste de integracao (usa modelo real).
    pub mod transcribe {
        use std::path::Path;

        use crate::error::AppResult;
        use crate::transcribe::{load_mono_16k, Transcriber};

        pub fn run(model: &Path, audio: &Path) -> AppResult<Vec<(f64, f64, String)>> {
            let samples = load_mono_16k(audio)?;
            let t = Transcriber::load(model)?;
            let segs = t.run(&samples, |_| {})?;
            Ok(segs
                .into_iter()
                .map(|s| (s.start_secs, s.end_secs, s.text))
                .collect())
        }
    }

    /// Diarizacao exposta para teste de integracao (roda o exe do sherpa).
    pub mod diarize {
        use std::path::Path;

        use crate::error::AppResult;

        /// Roda a diarizacao e devolve (start, end, cluster) por span de voz.
        pub fn run(
            exe: &Path,
            segmentation: &Path,
            embedding: &Path,
            audio: &Path,
            num_speakers: Option<i32>,
        ) -> AppResult<Vec<(f64, f64, i64)>> {
            let spans = crate::diarize::run(exe, segmentation, embedding, audio, num_speakers)?;
            Ok(spans
                .into_iter()
                .map(|s| (s.start_secs, s.end_secs, s.cluster))
                .collect())
        }
    }

    /// Extracao de embedding exposta para teste de integracao.
    pub mod speaker {
        use std::path::Path;

        use crate::error::AppResult;
        use crate::speaker;
        use crate::transcribe;

        pub fn run(
            executable: &Path,
            model: &Path,
            library: &Path,
            audio: &Path,
        ) -> AppResult<usize> {
            let samples = transcribe::load_mono_16k(audio)?;
            speaker::extract_isolated(executable, library, model, &samples)
                .map(|embedding| embedding.len())
        }
    }
}

use std::sync::Arc;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, State};

use crate::db::meetings::{self, Meeting};
use crate::db::segments::{self, TranscriptSegment};
use crate::db::settings::{self, AppSettings, SettingsPatch};
use crate::db::speakers::{self, SpeakerProfile};
use crate::db::summary::{self, StoredActionItem, StoredSummary};
use crate::db::Db;
use crate::diagnostics::SystemDiagnostics;
use crate::error::AppResult;
use crate::models::{ModelInfo, ModelManager};
use crate::paths::AppPaths;
use crate::pipeline::Pipeline;
use crate::session::{RecordingState, SessionManager};

/// Estado global compartilhado entre os comandos.
struct AppState {
    paths: AppPaths,
    db: Db,
    models: Arc<ModelManager>,
    session: Arc<SessionManager>,
    // Mantem o worker de escrita vivo durante toda a execucao do app.
    _log_guard: tracing_appender::non_blocking::WorkerGuard,
}

const MAX_LOG_BYTES: u64 = 5 * 1024 * 1024;

#[derive(Debug, Clone, Serialize)]
struct LogInfo {
    bytes: u64,
    max_bytes: u64,
}

fn cap_log_file(path: &std::path::Path) -> AppResult<()> {
    use std::io::{Read, Seek, SeekFrom};

    let size = match std::fs::metadata(path) {
        Ok(metadata) => metadata.len(),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error.into()),
    };
    if size <= MAX_LOG_BYTES {
        return Ok(());
    }
    let keep_bytes = MAX_LOG_BYTES / 2;
    let mut file = std::fs::File::open(path)?;
    file.seek(SeekFrom::Start(size.saturating_sub(keep_bytes)))?;
    let mut tail = Vec::with_capacity(keep_bytes as usize);
    file.read_to_end(&mut tail)?;
    let mut output = b"[log reduzido pelo limite de 5 MB]\n".to_vec();
    output.extend_from_slice(&tail);
    std::fs::write(path, output)?;
    Ok(())
}

#[tauri::command]
fn run_diagnostics(state: State<'_, AppState>) -> AppResult<SystemDiagnostics> {
    diagnostics::run(&state.paths)
}

#[tauri::command]
fn get_logs(state: State<'_, AppState>) -> AppResult<String> {
    const MAX_BYTES: usize = 200_000;
    let path = state.paths.logs_dir.join("atalocal.log");
    if !path.exists() {
        return Ok(String::new());
    }

    let bytes = std::fs::read(path)?;
    let start = bytes.len().saturating_sub(MAX_BYTES);
    Ok(String::from_utf8_lossy(&bytes[start..]).into_owned())
}

#[tauri::command]
fn get_log_info(state: State<'_, AppState>) -> AppResult<LogInfo> {
    let path = state.paths.logs_dir.join("atalocal.log");
    let bytes = std::fs::metadata(path)
        .map(|metadata| metadata.len())
        .unwrap_or(0);
    Ok(LogInfo {
        bytes,
        max_bytes: MAX_LOG_BYTES,
    })
}

#[tauri::command]
fn list_speaker_profiles(state: State<'_, AppState>) -> AppResult<Vec<SpeakerProfile>> {
    speakers::list(&state.db)
}

#[tauri::command]
fn list_models(state: State<'_, AppState>) -> AppResult<Vec<ModelInfo>> {
    state.models.list()
}

/// Modelos de transcricao com caracteristicas e recomendacao para esta maquina.
#[tauri::command]
fn whisper_options() -> Vec<models::catalog::WhisperOption> {
    models::catalog::whisper_options(models::available_ram_mb())
}

#[tauri::command]
async fn download_model(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    model_id: String,
) -> AppResult<()> {
    let manager = state.models.clone();
    let app_handle = app.clone();
    manager
        .download(&model_id, move |progress| {
            let _ = app_handle.emit("model://download-progress", &progress);
        })
        .await
}

#[tauri::command]
fn cancel_model_download(state: State<'_, AppState>, model_id: String) {
    state.models.cancel(&model_id);
}

#[tauri::command]
async fn verify_model(state: State<'_, AppState>, model_id: String) -> AppResult<ModelInfo> {
    state.models.verify(&model_id).await
}

#[tauri::command]
fn remove_model(state: State<'_, AppState>, model_id: String) -> AppResult<()> {
    state.models.remove(&model_id)
}

#[tauri::command]
fn get_settings(state: State<'_, AppState>) -> AppResult<AppSettings> {
    settings::load(&state.db, &state.paths)
}

#[tauri::command]
fn update_settings(state: State<'_, AppState>, patch: SettingsPatch) -> AppResult<AppSettings> {
    settings::apply_patch(&state.db, &state.paths, patch)
}

// ---- Gravacao ----

#[tauri::command]
fn start_recording(
    state: State<'_, AppState>,
    title: String,
    device: Option<String>,
) -> AppResult<Meeting> {
    let title = if title.trim().is_empty() {
        format!(
            "Reuniao de {}",
            chrono::Local::now().format("%d/%m/%Y %H:%M")
        )
    } else {
        title
    };
    state.session.start(&title, device)
}

fn spawn_pipeline(app: &AppHandle, state: &AppState, meeting_id: String) -> AppResult<()> {
    let app = app.clone();
    let db = state.db.clone();
    let paths = state.paths.clone();
    let models = state.models.clone();
    std::thread::Builder::new()
        .name("atalocal-pipeline".into())
        .spawn(move || {
            run_pipeline(app, db, paths, models, meeting_id);
        })
        .map(|_| ())
        .map_err(|e| crate::error::AppError::Other(e.to_string()))
}

fn run_pipeline(
    app: AppHandle,
    db: crate::db::Db,
    paths: AppPaths,
    models: Arc<ModelManager>,
    meeting_id: String,
) {
    let id = meeting_id.clone();
    let pipeline = Arc::new(Pipeline::new(db, paths, models, app));
    if let Err(e) = pipeline.run(meeting_id) {
        tracing::error!(meeting = %id, "pipeline falhou: {e}");
    }
}

#[tauri::command]
fn stop_recording(app: AppHandle, state: State<'_, AppState>) -> AppResult<String> {
    let meeting_id = state.session.stop()?;
    // Dispara o processamento em seguida (transcricao -> ...).
    spawn_pipeline(&app, &state, meeting_id.clone())?;
    Ok(meeting_id)
}

#[tauri::command]
fn cancel_recording(state: State<'_, AppState>) -> AppResult<()> {
    state.session.cancel()
}

#[tauri::command]
fn recording_state(state: State<'_, AppState>) -> RecordingState {
    state.session.state()
}

#[tauri::command]
fn list_meetings(state: State<'_, AppState>) -> AppResult<Vec<Meeting>> {
    meetings::list(&state.db)
}

#[tauri::command]
fn get_meeting(state: State<'_, AppState>, meeting_id: String) -> AppResult<Meeting> {
    meetings::get(&state.db, &meeting_id)
}

#[tauri::command]
fn delete_meeting(state: State<'_, AppState>, meeting_id: String) -> AppResult<()> {
    meetings::delete(&state.db, &meeting_id)
}

#[tauri::command]
fn list_segments(
    state: State<'_, AppState>,
    meeting_id: String,
) -> AppResult<Vec<TranscriptSegment>> {
    segments::list(&state.db, &meeting_id)
}

#[tauri::command]
fn enroll_speaker_from_meeting(
    state: State<'_, AppState>,
    meeting_id: String,
    cluster: i64,
    name: String,
) -> AppResult<SpeakerProfile> {
    let meeting = meetings::get(&state.db, &meeting_id)?;
    let audio = meeting
        .audio_path
        .as_ref()
        .ok_or_else(|| crate::error::AppError::Other("reuniao sem audio".into()))?;
    let all_segments = segments::list(&state.db, &meeting_id)?;
    let ranges: Vec<(f64, f64)> = all_segments
        .iter()
        .filter(|segment| segment.cluster == Some(cluster))
        .map(|segment| (segment.start_secs, segment.end_secs))
        .collect();
    if ranges.is_empty() {
        return Err(crate::error::AppError::Other(
            "essa voz ainda nao tem segmentos identificados".into(),
        ));
    }

    let samples = transcribe::load_mono_16k(&std::path::PathBuf::from(audio))?;
    let selected = speaker::samples_for_ranges(&samples, &ranges);
    let (embedding_model, library) = speaker::model_paths(&state.models)?;
    let helper = speaker::helper_executable()?;
    let embedding = speaker::extract_isolated(&helper, &library, &embedding_model, &selected)?;
    let profile = speakers::upsert(&state.db, &name, &embedding)?;
    segments::set_speaker_for_cluster(&state.db, &meeting_id, cluster, &profile.id, 1.0)?;
    Ok(profile)
}

#[tauri::command]
fn get_summary(state: State<'_, AppState>, meeting_id: String) -> AppResult<Option<StoredSummary>> {
    summary::get(&state.db, &meeting_id)
}

#[tauri::command]
fn list_actions(
    state: State<'_, AppState>,
    meeting_id: String,
) -> AppResult<Vec<StoredActionItem>> {
    summary::list_actions(&state.db, &meeting_id)
}

/// Inicia (ou retoma) o processamento de uma reuniao numa thread dedicada.
#[tauri::command]
fn process_meeting(
    app: AppHandle,
    state: State<'_, AppState>,
    meeting_id: String,
) -> AppResult<()> {
    let meeting = meetings::get(&state.db, &meeting_id)?;
    if meeting.stage == meetings::Stage::Completed
        && summary::get(&state.db, &meeting_id)?.is_none()
    {
        meetings::set_stage(&state.db, &meeting_id, meetings::Stage::Summarizing, None)?;
    } else if meeting.stage == meetings::Stage::Failed {
        let stage = if meeting
            .error
            .as_deref()
            .is_some_and(|e| e.contains("ata nao gerada"))
        {
            meetings::Stage::Summarizing
        } else {
            meetings::Stage::Finalizing
        };
        meetings::set_stage(&state.db, &meeting_id, stage, None)?;
    }
    spawn_pipeline(&app, &state, meeting_id)
}

fn resume_stale_pipelines(app: &AppHandle, state: &AppState) {
    let Ok(list) = meetings::list(&state.db) else {
        return;
    };
    let mut ids = Vec::new();
    for meeting in list {
        if matches!(
            meeting.stage,
            meetings::Stage::Finalizing
                | meetings::Stage::Transcribing
                | meetings::Stage::Diarizing
                | meetings::Stage::Identifying
                | meetings::Stage::Summarizing
        ) {
            ids.push(meeting.id);
        } else if meeting.stage == meetings::Stage::Completed
            && matches!(summary::get(&state.db, &meeting.id), Ok(None))
            && segments::count(&state.db, &meeting.id).unwrap_or(0) > 0
        {
            let _ = meetings::set_stage(&state.db, &meeting.id, meetings::Stage::Summarizing, None);
            ids.push(meeting.id);
        }
    }

    if ids.is_empty() {
        return;
    }

    let app = app.clone();
    let db = state.db.clone();
    let paths = state.paths.clone();
    let models = state.models.clone();
    let _ = std::thread::Builder::new()
        .name("atalocal-pipeline-recovery".into())
        .spawn(move || {
            for id in ids {
                run_pipeline(app.clone(), db.clone(), paths.clone(), models.clone(), id);
            }
        })
        .map_err(|e| tracing::error!("retomada dos pipelines falhou: {e}"));
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    if std::env::args().nth(1).as_deref() == Some("--speaker-helper") {
        std::process::exit(speaker::run_helper(std::env::args()));
    }

    tauri::Builder::default()
        .plugin(tauri_plugin_os::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .setup(move |app| {
            // No Android, ProjectDirs usa convencoes de Linux e pode resolver
            // para um diretorio de trabalho sem permissao de escrita. O
            // resolver do Tauri aponta para a pasta privada do aplicativo.
            let base = app.path().app_data_dir().map_err(|error| {
                std::io::Error::other(format!(
                    "nao foi possivel localizar os dados do app: {error}"
                ))
            })?;
            let paths = AppPaths::from_base(base)?;

            let log_path = paths.logs_dir.join("atalocal.log");
            let _ = cap_log_file(&log_path);
            let file_appender = tracing_appender::rolling::never(&paths.logs_dir, "atalocal.log");
            let (log_writer, log_guard) = tracing_appender::non_blocking(file_appender);
            let _ = tracing_subscriber::fmt()
                .with_env_filter(
                    tracing_subscriber::EnvFilter::try_from_default_env()
                        .unwrap_or_else(|_| "info".into()),
                )
                .with_writer(log_writer)
                .try_init();

            let db = Db::open(&paths)?;
            let models = Arc::new(ModelManager::new(db.clone(), &paths));
            let session = Arc::new(SessionManager::new(db.clone(), paths.clone()));

            // Reuniao deixada em 'recording' por um fechamento abrupto nao tem como
            // continuar gravando; marca como 'failed' recuperavel na proxima abertura.
            if let Ok(list) = meetings::list(&db) {
                for m in list
                    .into_iter()
                    .filter(|m| m.stage == meetings::Stage::Recording)
                {
                    let _ = meetings::set_stage(
                        &db,
                        &m.id,
                        meetings::Stage::Failed,
                        Some("o aplicativo foi fechado durante a gravacao"),
                    );
                }
            }

            let state = AppState {
                paths,
                db,
                models,
                session,
                _log_guard: log_guard,
            };
            resume_stale_pipelines(app.handle(), &state);
            app.manage(state);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            run_diagnostics,
            get_logs,
            get_log_info,
            list_speaker_profiles,
            enroll_speaker_from_meeting,
            list_models,
            whisper_options,
            download_model,
            cancel_model_download,
            verify_model,
            remove_model,
            get_settings,
            update_settings,
            start_recording,
            stop_recording,
            cancel_recording,
            recording_state,
            list_meetings,
            get_meeting,
            delete_meeting,
            list_segments,
            get_summary,
            list_actions,
            process_meeting,
        ])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o AtaLocal");
}
