use serde::{Deserialize, Serialize};

use crate::db::Db;
use crate::error::AppResult;
use crate::paths::AppPaths;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    pub input_device: Option<String>,
    pub whisper_model: String,
    pub retention_days: Option<i64>,
    pub data_dir: String,
    pub models_dir: String,
    pub low_power_mode: bool,
}

impl AppSettings {
    fn defaults(paths: &AppPaths) -> Self {
        Self {
            input_device: None,
            // Vazio = o app recomenda um modelo com base na RAM na 1a execucao.
            whisper_model: String::new(),
            retention_days: None,
            data_dir: paths.data_dir.to_string_lossy().into_owned(),
            models_dir: paths.models_dir.to_string_lossy().into_owned(),
            low_power_mode: false,
        }
    }
}

/// Patch parcial vindo do frontend.
#[derive(Debug, Default, Deserialize)]
pub struct SettingsPatch {
    pub input_device: Option<Option<String>>,
    pub whisper_model: Option<String>,
    pub retention_days: Option<Option<i64>>,
    pub low_power_mode: Option<bool>,
}

const KEY: &str = "app_settings";

pub fn load(db: &Db, paths: &AppPaths) -> AppResult<AppSettings> {
    db.with(|conn| {
        let raw: Option<String> = conn
            .query_row(
                "SELECT value FROM app_settings WHERE key = ?1",
                [KEY],
                |r| r.get(0),
            )
            .ok();

        let mut settings = match raw {
            Some(s) => serde_json::from_str(&s).unwrap_or_else(|_| AppSettings::defaults(paths)),
            None => AppSettings::defaults(paths),
        };
        // Diretorios sao sempre resolvidos em runtime, nunca persistidos como verdade.
        settings.data_dir = paths.data_dir.to_string_lossy().into_owned();
        settings.models_dir = paths.models_dir.to_string_lossy().into_owned();
        Ok(settings)
    })
}

pub fn save(db: &Db, settings: &AppSettings) -> AppResult<()> {
    let json = serde_json::to_string(settings)?;
    db.with(|conn| {
        conn.execute(
            "INSERT INTO app_settings(key, value) VALUES(?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (KEY, &json),
        )?;
        Ok(())
    })
}

pub fn apply_patch(db: &Db, paths: &AppPaths, patch: SettingsPatch) -> AppResult<AppSettings> {
    let mut s = load(db, paths)?;
    if let Some(v) = patch.input_device {
        s.input_device = v;
    }
    if let Some(v) = patch.whisper_model {
        s.whisper_model = v;
    }
    if let Some(v) = patch.retention_days {
        s.retention_days = v;
    }
    if let Some(v) = patch.low_power_mode {
        s.low_power_mode = v;
    }
    save(db, &s)?;
    Ok(s)
}
