use std::sync::Arc;

use parking_lot::Mutex;
use rusqlite::Connection;

use crate::error::AppResult;
use crate::paths::AppPaths;

pub mod meetings;
pub mod segments;
pub mod settings;

/// Conexao SQLite compartilhada. Serializada por um Mutex — o volume de
/// escrita do app e baixo e isso evita toda a complexidade de um pool.
#[derive(Clone)]
pub struct Db {
    conn: Arc<Mutex<Connection>>,
}

impl Db {
    pub fn open(paths: &AppPaths) -> AppResult<Self> {
        let conn = Connection::open(&paths.db_path)?;
        conn.execute_batch(include_str!("schema.sql"))?;
        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    /// Executa uma operacao com a conexao travada.
    pub fn with<T>(&self, f: impl FnOnce(&Connection) -> AppResult<T>) -> AppResult<T> {
        let guard = self.conn.lock();
        f(&guard)
    }
}
