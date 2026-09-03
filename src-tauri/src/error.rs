use serde::Serialize;

/// Erro unificado do backend. Serializa como string para o frontend.
#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("banco de dados: {0}")]
    Db(#[from] rusqlite::Error),

    #[error("io: {0}")]
    Io(#[from] std::io::Error),

    #[error("rede: {0}")]
    Http(#[from] reqwest::Error),

    #[error("json: {0}")]
    Json(#[from] serde_json::Error),

    #[error("audio: {0}")]
    #[allow(dead_code)] // usado a partir da Fase 2 (captura)
    Audio(String),

    #[error("modelo: {0}")]
    Model(String),

    #[error("checksum divergente para {id}: esperado {expected}, obtido {actual}")]
    Checksum {
        id: String,
        expected: String,
        actual: String,
    },

    #[error("operacao cancelada")]
    Cancelled,

    #[error("{0}")]
    #[allow(dead_code)] // erro generico para uso futuro
    Other(String),
}

impl Serialize for AppError {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

pub type AppResult<T> = Result<T, AppError>;
