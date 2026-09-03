use serde::Serialize;

use crate::db::Db;
use crate::error::AppResult;
use crate::summarize::{ActionItemDraft, MeetingMinutes, SummaryEntry};

#[derive(Debug, Clone, Serialize)]
pub struct StoredSummary {
    pub meeting_id: String,
    pub executive_summary: String,
    pub topics: Vec<String>,
    pub decisions: Vec<SummaryEntry>,
    pub pending: Vec<SummaryEntry>,
    pub divergences: Vec<SummaryEntry>,
    pub next_steps: Vec<String>,
    pub generated_at: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct StoredActionItem {
    pub id: String,
    pub meeting_id: String,
    pub description: String,
    pub assignee: Option<String>,
    pub due: Option<String>,
    pub status: String,
}

/// Substitui a ata e as tarefas de uma reuniao (idempotente ao reprocessar).
pub fn replace(db: &Db, meeting_id: &str, minutes: &MeetingMinutes) -> AppResult<()> {
    let now = chrono::Utc::now().to_rfc3339();
    let topics = serde_json::to_string(&minutes.topics)?;
    let decisions = serde_json::to_string(&minutes.decisions)?;
    let pending = serde_json::to_string(&minutes.pending)?;
    let divergences = serde_json::to_string(&minutes.divergences)?;
    let next_steps = serde_json::to_string(&minutes.next_steps)?;

    db.with(|conn| {
        let tx = conn.unchecked_transaction()?;
        tx.execute(
            "INSERT INTO meeting_summary
               (meeting_id, executive_summary, topics, decisions, pending,
                divergences, next_steps, generated_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
             ON CONFLICT(meeting_id) DO UPDATE SET
               executive_summary = excluded.executive_summary,
               topics = excluded.topics,
               decisions = excluded.decisions,
               pending = excluded.pending,
               divergences = excluded.divergences,
               next_steps = excluded.next_steps,
               generated_at = excluded.generated_at",
            (
                meeting_id,
                &minutes.executive_summary,
                &topics,
                &decisions,
                &pending,
                &divergences,
                &next_steps,
                &now,
            ),
        )?;

        tx.execute("DELETE FROM action_item WHERE meeting_id = ?1", [meeting_id])?;
        {
            let mut stmt = tx.prepare(
                "INSERT INTO action_item
                   (id, meeting_id, description, assignee, due, status, source_segment_ids)
                 VALUES (?1, ?2, ?3, ?4, ?5, 'open', '[]')",
            )?;
            for a in &minutes.action_items {
                let ActionItemDraft {
                    description,
                    assignee,
                    due,
                } = a;
                stmt.execute((
                    uuid::Uuid::new_v4().to_string(),
                    meeting_id,
                    description,
                    assignee,
                    due,
                ))?;
            }
        }
        tx.commit()?;
        Ok(())
    })
}

pub fn get(db: &Db, meeting_id: &str) -> AppResult<Option<StoredSummary>> {
    db.with(|conn| {
        let row = conn
            .query_row(
                "SELECT executive_summary, topics, decisions, pending, divergences,
                        next_steps, generated_at
                 FROM meeting_summary WHERE meeting_id = ?1",
                [meeting_id],
                |r| {
                    Ok((
                        r.get::<_, String>(0)?,
                        r.get::<_, String>(1)?,
                        r.get::<_, String>(2)?,
                        r.get::<_, String>(3)?,
                        r.get::<_, String>(4)?,
                        r.get::<_, String>(5)?,
                        r.get::<_, String>(6)?,
                    ))
                },
            )
            .ok();
        Ok(row.map(|(es, t, d, p, dv, ns, ga)| StoredSummary {
            meeting_id: meeting_id.to_string(),
            executive_summary: es,
            topics: serde_json::from_str(&t).unwrap_or_default(),
            decisions: serde_json::from_str(&d).unwrap_or_default(),
            pending: serde_json::from_str(&p).unwrap_or_default(),
            divergences: serde_json::from_str(&dv).unwrap_or_default(),
            next_steps: serde_json::from_str(&ns).unwrap_or_default(),
            generated_at: ga,
        }))
    })
}

pub fn list_actions(db: &Db, meeting_id: &str) -> AppResult<Vec<StoredActionItem>> {
    db.with(|conn| {
        let mut stmt = conn.prepare(
            "SELECT id, description, assignee, due, status
             FROM action_item WHERE meeting_id = ?1 ORDER BY rowid",
        )?;
        let rows = stmt
            .query_map([meeting_id], |r| {
                Ok(StoredActionItem {
                    id: r.get(0)?,
                    meeting_id: meeting_id.to_string(),
                    description: r.get(1)?,
                    assignee: r.get(2)?,
                    due: r.get(3)?,
                    status: r.get(4)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    })
}
