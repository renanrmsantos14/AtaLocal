mod audio;
mod db;
mod diagnostics;
mod error;
mod models;
mod paths;

use std::sync::Arc;

use tauri::{Emitter, Manager, State};

use crate::db::settings::{self, AppSettings, SettingsPatch};
use crate::db::Db;
use crate::diagnostics::SystemDiagnostics;
use crate::error::AppResult;
use crate::models::{ModelInfo, ModelManager};
use crate::paths::AppPaths;

/// Estado global compartilhado entre os comandos.
struct AppState {
    paths: AppPaths,
    db: Db,
    models: Arc<ModelManager>,
}

#[tauri::command]
fn run_diagnostics(state: State<'_, AppState>) -> AppResult<SystemDiagnostics> {
    diagnostics::run(&state.paths)
}

#[tauri::command]
fn list_models(state: State<'_, AppState>) -> AppResult<Vec<ModelInfo>> {
    state.models.list()
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
fn update_settings(
    state: State<'_, AppState>,
    patch: SettingsPatch,
) -> AppResult<AppSettings> {
    settings::apply_patch(&state.db, &state.paths, patch)
}

pub fn run() {
    let paths = AppPaths::resolve().expect("nao foi possivel preparar os diretorios locais");

    let _ = tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info".into()),
        )
        .try_init();

    let db = Db::open(&paths).expect("nao foi possivel abrir o banco local");
    let models = Arc::new(ModelManager::new(db.clone(), &paths));

    tauri::Builder::default()
        .plugin(tauri_plugin_os::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .setup(move |app| {
            app.manage(AppState {
                paths: paths.clone(),
                db: db.clone(),
                models: models.clone(),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            run_diagnostics,
            list_models,
            download_model,
            cancel_model_download,
            verify_model,
            remove_model,
            get_settings,
            update_settings,
        ])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o AtaLocal");
}
