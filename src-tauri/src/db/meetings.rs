use serde::{Deserialize, Serialize};

use crate::db::Db;
use crate::error::{AppError, AppResult};

/// Estados de processamento de uma reuniao. A ordem reflete o pipeline;
/// cada etapa e retomavel sem repetir as anteriores.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Stage {
    Recording,
    Finalizing,
    Transcribing,
    Diarizing,
    Identifying,
    Summarizing,
    Completed,
    Failed,
    Cancelled,
}

impl Stage {
    pub fn as_str(self) -> &'static str {
        match self {
            Stage::Recording => "recording",
            Stage::Finalizing => "finalizing",
            Stage::Transcribing => "transcribing",
            Stage::Diarizing => "diarizing",
            Stage::Identifying => "identifying",
            Stage::Summarizing => "summarizing",
            Stage::Completed => "completed",
            Stage::Failed => "failed",
            Stage::Cancelled => "cancelled",
        }
    }

    pub fn parse(s: &str) -> AppResult<Self> {
        Ok(match s {
            "recording" => Stage::Recording,
            "finalizing" => Stage::Finalizing,
            "transcribing" => Stage::Transcribing,
            "diarizing" => Stage::Diarizing,
            "identifying" => Stage::Identifying,
            "summarizing" => Stage::Summarizing,
            "completed" => Stage::Completed,
            "failed" => Stage::Failed,
            "cancelled" => Stage::Cancelled,
            other => return Err(AppError::Other(format!("estado invalido: {other}"))),
        })
    }

    /// Proxima etapa do caminho feliz, ou None se terminal.
    #[allow(dead_code)] // usado pelo runner de pipeline (Fase 2 — transcricao+)
    pub fn next_happy(self) -> Option<Stage> {
        Some(match self {
            Stage::Recording => Stage::Finalizing,
            Stage::Finalizing => Stage::Transcribing,
            Stage::Transcribing => Stage::Diarizing,
            Stage::Diarizing => Stage::Identifying,
            Stage::Identifying => Stage::Summarizing,
            Stage::Summarizing => Stage::Completed,
            Stage::Completed | Stage::Failed | Stage::Cancelled => return None,
        })
    }

    #[allow(dead_code)] // usado pelo runner de pipeline (Fase 2 — transcricao+)
    pub fn is_terminal(self) -> bool {
        matches!(self, Stage::Completed | Stage::Failed | Stage::Cancelled)
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct Meeting {
    pub id: String,
    pub title: String,
    pub started_at: String,
    pub ended_at: Option<String>,
    pub duration_secs: f64,
    pub audio_path: Option<String>,
    pub stage: Stage,
    pub error: Option<String>,
    pub created_at: String,
}

fn row_to_meeting(r: &rusqlite::Row) -> rusqlite::Result<Meeting> {
    Ok(Meeting {
        id: r.get(0)?,
        title: r.get(1)?,
        started_at: r.get(2)?,
        ended_at: r.get(3)?,
        duration_secs: r.get(4)?,
        audio_path: r.get(5)?,
        stage: Stage::parse(&r.get::<_, String>(6)?)
            .unwrap_or(Stage::Failed),
        error: r.get(7)?,
        created_at: r.get(8)?,
    })
}

const COLS: &str =
    "id, title, started_at, ended_at, duration_secs, audio_path, stage, error, created_at";

pub fn create(db: &Db, title: &str) -> AppResult<Meeting> {
    let id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Utc::now().to_rfc3339();
    db.with(|conn| {
        conn.execute(
            "INSERT INTO meeting(id, title, started_at, duration_secs, stage, created_at)
             VALUES(?1, ?2, ?3, 0, 'recording', ?3)",
            (&id, title, &now),
        )?;
        Ok(())
    })?;
    get(db, &id)
}

pub fn get(db: &Db, id: &str) -> AppResult<Meeting> {
    db.with(|conn| {
        conn.query_row(
            &format!("SELECT {COLS} FROM meeting WHERE id = ?1"),
            [id],
            |r| row_to_meeting(r),
        )
        .map_err(Into::into)
    })
}

pub fn list(db: &Db) -> AppResult<Vec<Meeting>> {
    db.with(|conn| {
        let mut stmt =
            conn.prepare(&format!("SELECT {COLS} FROM meeting ORDER BY started_at DESC"))?;
        let rows = stmt
            .query_map([], |r| row_to_meeting(r))?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    })
}

pub fn set_stage(db: &Db, id: &str, stage: Stage, error: Option<&str>) -> AppResult<()> {
    db.with(|conn| {
        conn.execute(
            "UPDATE meeting SET stage = ?2, error = ?3 WHERE id = ?1",
            (id, stage.as_str(), error),
        )?;
        Ok(())
    })
}

pub fn finish_recording(
    db: &Db,
    id: &str,
    duration_secs: f64,
    audio_path: &str,
) -> AppResult<()> {
    let now = chrono::Utc::now().to_rfc3339();
    db.with(|conn| {
        conn.execute(
            "UPDATE meeting
             SET ended_at = ?2, duration_secs = ?3, audio_path = ?4, stage = 'finalizing'
             WHERE id = ?1",
            (id, &now, duration_secs, audio_path),
        )?;
        Ok(())
    })
}

pub fn delete(db: &Db, id: &str) -> AppResult<()> {
    db.with(|conn| {
        conn.execute("DELETE FROM meeting WHERE id = ?1", [id])?;
        Ok(())
    })
}
