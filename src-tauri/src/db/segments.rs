use serde::Serialize;

use crate::db::Db;
use crate::error::AppResult;

#[derive(Debug, Clone, Serialize)]
pub struct TranscriptSegment {
    pub id: String,
    pub meeting_id: String,
    pub start_secs: f64,
    pub end_secs: f64,
    pub text: String,
    pub cluster: Option<i64>,
    pub speaker_id: Option<String>,
    pub confidence: f64,
}

pub struct NewSegment {
    pub start_secs: f64,
    pub end_secs: f64,
    pub text: String,
}

/// Substitui todos os segmentos de uma reuniao (retranscricao e idempotente).
pub fn replace_all(db: &Db, meeting_id: &str, segments: &[NewSegment]) -> AppResult<()> {
    db.with(|conn| {
        let tx = conn.unchecked_transaction()?;
        tx.execute(
            "DELETE FROM transcript_segment WHERE meeting_id = ?1",
            [meeting_id],
        )?;
        {
            let mut stmt = tx.prepare(
                "INSERT INTO transcript_segment
                   (id, meeting_id, start_secs, end_secs, text, cluster, speaker_id, confidence)
                 VALUES (?1, ?2, ?3, ?4, ?5, NULL, NULL, 0)",
            )?;
            for s in segments {
                stmt.execute((
                    uuid::Uuid::new_v4().to_string(),
                    meeting_id,
                    s.start_secs,
                    s.end_secs,
                    &s.text,
                ))?;
            }
        }
        tx.commit()?;
        Ok(())
    })
}

pub fn list(db: &Db, meeting_id: &str) -> AppResult<Vec<TranscriptSegment>> {
    db.with(|conn| {
        let mut stmt = conn.prepare(
            "SELECT id, meeting_id, start_secs, end_secs, text, cluster, speaker_id, confidence
             FROM transcript_segment WHERE meeting_id = ?1 ORDER BY start_secs",
        )?;
        let rows = stmt
            .query_map([meeting_id], |r| {
                Ok(TranscriptSegment {
                    id: r.get(0)?,
                    meeting_id: r.get(1)?,
                    start_secs: r.get(2)?,
                    end_secs: r.get(3)?,
                    text: r.get(4)?,
                    cluster: r.get(5)?,
                    speaker_id: r.get(6)?,
                    confidence: r.get(7)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    })
}

#[allow(dead_code)] // exposto para diagnostico/telas de progresso
pub fn count(db: &Db, meeting_id: &str) -> AppResult<i64> {
    db.with(|conn| {
        conn.query_row(
            "SELECT COUNT(*) FROM transcript_segment WHERE meeting_id = ?1",
            [meeting_id],
            |r| r.get(0),
        )
        .map_err(Into::into)
    })
}
