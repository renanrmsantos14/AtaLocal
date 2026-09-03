use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Instant;

use futures_util::StreamExt;
use parking_lot::Mutex;
use serde::Serialize;
use sha2::{Digest, Sha256};
use tokio::io::{AsyncSeekExt, AsyncWriteExt};
use tokio::sync::watch;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::paths::AppPaths;

pub mod catalog;
use catalog::{ModelDef, ModelKind};

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ModelStatus {
    NotDownloaded,
    Downloading,
    Verifying,
    Ready,
    Corrupt,
    Failed,
}

impl ModelStatus {
    fn as_str(self) -> &'static str {
        match self {
            Self::NotDownloaded => "not_downloaded",
            Self::Downloading => "downloading",
            Self::Verifying => "verifying",
            Self::Ready => "ready",
            Self::Corrupt => "corrupt",
            Self::Failed => "failed",
        }
    }
    fn parse(s: &str) -> Self {
        match s {
            "downloading" => Self::Downloading,
            "verifying" => Self::Verifying,
            "ready" => Self::Ready,
            "corrupt" => Self::Corrupt,
            "failed" => Self::Failed,
            _ => Self::NotDownloaded,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct ModelInfo {
    pub id: String,
    pub kind: ModelKind,
    pub filename: String,
    pub url: String,
    pub sha256: String,
    pub size_bytes: u64,
    pub status: ModelStatus,
    pub downloaded_bytes: u64,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct DownloadProgress {
    pub model_id: String,
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub speed: f64,
    pub status: ModelStatus,
}

struct PersistedState {
    status: ModelStatus,
    downloaded_bytes: u64,
    error: Option<String>,
}

/// Gerencia catalogo, estado persistido e downloads em andamento.
pub struct ModelManager {
    db: Db,
    dir: PathBuf,
    /// Canais de cancelamento por modelo em download.
    cancels: Arc<Mutex<HashMap<String, watch::Sender<bool>>>>,
}

impl ModelManager {
    pub fn new(db: Db, paths: &AppPaths) -> Self {
        Self {
            db,
            dir: paths.models_dir.clone(),
            cancels: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    fn path_of(&self, def: &ModelDef) -> PathBuf {
        self.dir.join(def.filename)
    }
    fn part_path_of(&self, def: &ModelDef) -> PathBuf {
        self.dir.join(format!("{}.part", def.filename))
    }

    fn load_state(&self, id: &str) -> AppResult<PersistedState> {
        self.db.with(|conn| {
            let row = conn
                .query_row(
                    "SELECT status, downloaded_bytes, error FROM model_state WHERE id = ?1",
                    [id],
                    |r| {
                        Ok((
                            r.get::<_, String>(0)?,
                            r.get::<_, i64>(1)?,
                            r.get::<_, Option<String>>(2)?,
                        ))
                    },
                )
                .ok();
            Ok(match row {
                Some((s, b, e)) => PersistedState {
                    status: ModelStatus::parse(&s),
                    downloaded_bytes: b.max(0) as u64,
                    error: e,
                },
                None => PersistedState {
                    status: ModelStatus::NotDownloaded,
                    downloaded_bytes: 0,
                    error: None,
                },
            })
        })
    }

    fn save_state(&self, id: &str, st: ModelStatus, bytes: u64, err: Option<&str>) -> AppResult<()> {
        let now = chrono::Utc::now().to_rfc3339();
        self.db.with(|conn| {
            conn.execute(
                "INSERT INTO model_state(id, status, downloaded_bytes, error, updated_at)
                 VALUES(?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(id) DO UPDATE SET
                   status = excluded.status,
                   downloaded_bytes = excluded.downloaded_bytes,
                   error = excluded.error,
                   updated_at = excluded.updated_at",
                (id, st.as_str(), bytes as i64, err, &now),
            )?;
            Ok(())
        })
    }

    pub fn list(&self) -> AppResult<Vec<ModelInfo>> {
        catalog::CATALOG
            .iter()
            .map(|def| {
                let st = self.load_state(def.id)?;
                let file = self.path_of(def);
                // Se o arquivo final existe e o estado diz "ready", confia.
                // Caso contrario, deriva de bytes do .part.
                let (status, downloaded) = if st.status == ModelStatus::Ready && file.exists() {
                    (ModelStatus::Ready, def.size_bytes)
                } else {
                    let part = self.part_path_of(def);
                    let part_len = std::fs::metadata(&part).map(|m| m.len()).unwrap_or(0);
                    let dl = st.downloaded_bytes.max(part_len);
                    let status = if dl > 0 && st.status != ModelStatus::Downloading {
                        // Download parcial parado: mantem estado mas mostra bytes.
                        st.status
                    } else {
                        st.status
                    };
                    (status, dl)
                };
                Ok(ModelInfo {
                    id: def.id.to_string(),
                    kind: def.kind,
                    filename: def.filename.to_string(),
                    url: def.url.to_string(),
                    sha256: def.sha256.to_string(),
                    size_bytes: def.size_bytes,
                    status,
                    downloaded_bytes: downloaded,
                    error: st.error,
                })
            })
            .collect()
    }

    pub fn cancel(&self, id: &str) {
        if let Some(tx) = self.cancels.lock().get(id) {
            let _ = tx.send(true);
        }
    }

    /// Baixa (ou retoma) um modelo. Emite progresso via `on_progress`.
    /// Retomada: usa Range a partir do tamanho do `.part` local.
    pub async fn download<F>(&self, id: &str, on_progress: F) -> AppResult<()>
    where
        F: Fn(DownloadProgress) + Send + Sync + 'static,
    {
        let def = catalog::find(id).ok_or_else(|| AppError::Model(format!("id desconhecido: {id}")))?;
        let final_path = self.path_of(def);
        let part_path = self.part_path_of(def);

        if final_path.exists() && self.load_state(id)?.status == ModelStatus::Ready {
            return Ok(());
        }

        let (cancel_tx, mut cancel_rx) = watch::channel(false);
        self.cancels.lock().insert(id.to_string(), cancel_tx);
        let _guard = CancelGuard {
            map: self.cancels.clone(),
            id: id.to_string(),
        };

        let mut existing: u64 = std::fs::metadata(&part_path).map(|m| m.len()).unwrap_or(0);
        if existing > def.size_bytes {
            // .part corrompido/maior que o esperado: recomeca.
            let _ = std::fs::remove_file(&part_path);
            existing = 0;
        }

        let client = reqwest::Client::builder()
            .user_agent("AtaLocal/0.1")
            .build()?;
        let mut req = client.get(def.url);
        if existing > 0 {
            req = req.header(reqwest::header::RANGE, format!("bytes={existing}-"));
        }

        self.save_state(id, ModelStatus::Downloading, existing, None)?;

        let resp = req.send().await?.error_for_status()?;
        let resumed = resp.status() == reqwest::StatusCode::PARTIAL_CONTENT;
        let total = if resumed {
            existing + resp.content_length().unwrap_or(0)
        } else {
            resp.content_length().unwrap_or(def.size_bytes)
        };

        let mut file = tokio::fs::OpenOptions::new()
            .create(true)
            .write(true)
            .read(true)
            .open(&part_path)
            .await?;

        let mut written = if resumed {
            file.seek(std::io::SeekFrom::End(0)).await?;
            existing
        } else {
            file.set_len(0).await?;
            file.seek(std::io::SeekFrom::Start(0)).await?;
            0
        };

        let started = Instant::now();
        let mut last_emit = Instant::now();
        let mut stream = resp.bytes_stream();

        while let Some(chunk) = stream.next().await {
            if *cancel_rx.borrow_and_update() {
                file.flush().await?;
                self.save_state(id, ModelStatus::NotDownloaded, written, Some("cancelado"))?;
                return Err(AppError::Cancelled);
            }
            let bytes = chunk?;
            file.write_all(&bytes).await?;
            written += bytes.len() as u64;

            if last_emit.elapsed().as_millis() >= 250 {
                let speed = written.saturating_sub(existing) as f64
                    / started.elapsed().as_secs_f64().max(0.001);
                on_progress(DownloadProgress {
                    model_id: id.to_string(),
                    downloaded_bytes: written,
                    total_bytes: total,
                    speed,
                    status: ModelStatus::Downloading,
                });
                let _ = self.save_state(id, ModelStatus::Downloading, written, None);
                last_emit = Instant::now();
            }
        }
        file.flush().await?;
        drop(file);

        // Verificacao de checksum.
        on_progress(DownloadProgress {
            model_id: id.to_string(),
            downloaded_bytes: written,
            total_bytes: total,
            speed: 0.0,
            status: ModelStatus::Verifying,
        });
        self.save_state(id, ModelStatus::Verifying, written, None)?;

        let digest = sha256_file(&part_path).await?;
        if !def.sha256.is_empty() && digest != def.sha256 {
            self.save_state(id, ModelStatus::Corrupt, written, Some("checksum divergente"))?;
            let _ = std::fs::remove_file(&part_path);
            return Err(AppError::Checksum {
                id: id.to_string(),
                expected: def.sha256.to_string(),
                actual: digest,
            });
        }
        if def.sha256.is_empty() {
            tracing::warn!(model = id, sha256 = %digest, "checksum nao fixado; registrando o observado");
        }

        std::fs::rename(&part_path, &final_path)?;

        // Modelos do Sherpa vem em .tar.bz2: extrai ao lado, numa pasta com o id.
        if final_path.extension().and_then(|e| e.to_str()) == Some("bz2") {
            let dest = self.dir.join(id);
            if let Err(e) = extract_tar_bz2(&final_path, &dest) {
                self.save_state(id, ModelStatus::Failed, def.size_bytes, Some(&e.to_string()))?;
                return Err(e);
            }
        }

        self.save_state(id, ModelStatus::Ready, def.size_bytes, None)?;
        on_progress(DownloadProgress {
            model_id: id.to_string(),
            downloaded_bytes: def.size_bytes,
            total_bytes: total,
            speed: 0.0,
            status: ModelStatus::Ready,
        });
        Ok(())
    }

    pub async fn verify(&self, id: &str) -> AppResult<ModelInfo> {
        let def = catalog::find(id).ok_or_else(|| AppError::Model(format!("id desconhecido: {id}")))?;
        let path = self.path_of(def);
        if !path.exists() {
            self.save_state(id, ModelStatus::NotDownloaded, 0, None)?;
        } else {
            let digest = sha256_file(&path).await?;
            if !def.sha256.is_empty() && digest != def.sha256 {
                self.save_state(id, ModelStatus::Corrupt, 0, Some("checksum divergente"))?;
            } else {
                self.save_state(id, ModelStatus::Ready, def.size_bytes, None)?;
            }
        }
        self.list()?
            .into_iter()
            .find(|m| m.id == id)
            .ok_or_else(|| AppError::Model(id.to_string()))
    }

    pub fn remove(&self, id: &str) -> AppResult<()> {
        let def = catalog::find(id).ok_or_else(|| AppError::Model(format!("id desconhecido: {id}")))?;
        let _ = std::fs::remove_file(self.path_of(def));
        let _ = std::fs::remove_file(self.part_path_of(def));
        self.save_state(id, ModelStatus::NotDownloaded, 0, None)?;
        Ok(())
    }
}

struct CancelGuard {
    map: Arc<Mutex<HashMap<String, watch::Sender<bool>>>>,
    id: String,
}
impl Drop for CancelGuard {
    fn drop(&mut self) {
        self.map.lock().remove(&self.id);
    }
}

/// Extrai um `.tar.bz2` para `dest`, achatando o diretorio-raiz do arquivo
/// (os tarballs do Sherpa embrulham tudo numa pasta com o nome do modelo).
fn extract_tar_bz2(archive: &std::path::Path, dest: &std::path::Path) -> AppResult<()> {
    let file = std::fs::File::open(archive)?;
    let decompressor = bzip2::read::BzDecoder::new(file);
    let mut tar = tar::Archive::new(decompressor);

    let _ = std::fs::remove_dir_all(dest);
    std::fs::create_dir_all(dest)?;

    for entry in tar.entries().map_err(AppError::Io)? {
        let mut entry = entry.map_err(AppError::Io)?;
        let path = entry.path().map_err(AppError::Io)?.into_owned();
        // Remove o primeiro componente (a pasta-raiz do tarball).
        let stripped: std::path::PathBuf = path.components().skip(1).collect();
        if stripped.as_os_str().is_empty() {
            continue;
        }
        let out = dest.join(&stripped);
        if entry.header().entry_type().is_dir() {
            std::fs::create_dir_all(&out)?;
        } else {
            if let Some(parent) = out.parent() {
                std::fs::create_dir_all(parent)?;
            }
            entry.unpack(&out).map_err(AppError::Io)?;
        }
    }
    Ok(())
}

async fn sha256_file(path: &std::path::Path) -> AppResult<String> {
    use tokio::io::AsyncReadExt;
    let mut file = tokio::fs::File::open(path).await?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 1 << 20];
    loop {
        let n = file.read(&mut buf).await?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}
