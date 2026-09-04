use std::path::PathBuf;

/// Diretorios locais do aplicativo. Tudo fica sob a pasta de dados do usuario.
#[derive(Clone, Debug)]
pub struct AppPaths {
    pub data_dir: PathBuf,
    pub models_dir: PathBuf,
    pub recordings_dir: PathBuf,
    pub logs_dir: PathBuf,
    pub db_path: PathBuf,
}

impl AppPaths {
    pub fn from_base(base: PathBuf) -> std::io::Result<Self> {
        let paths = Self {
            models_dir: base.join("models"),
            recordings_dir: base.join("recordings"),
            logs_dir: base.join("logs"),
            db_path: base.join("atalocal.db"),
            data_dir: base,
        };

        for dir in [
            &paths.data_dir,
            &paths.models_dir,
            &paths.recordings_dir,
            &paths.logs_dir,
        ] {
            std::fs::create_dir_all(dir)?;
        }

        Ok(paths)
    }
}
