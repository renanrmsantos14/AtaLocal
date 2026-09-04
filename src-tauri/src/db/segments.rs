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
    pub speaker_name: Option<String>,
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
            "SELECT s.id, s.meeting_id, s.start_secs, s.end_secs, s.text, s.cluster,
                    s.speaker_id, p.name, s.confidence
             FROM transcript_segment s
             LEFT JOIN speaker_profile p ON p.id = s.speaker_id
             WHERE s.meeting_id = ?1 ORDER BY s.start_secs",
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
                    speaker_name: r.get(7)?,
                    confidence: r.get(8)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    })
}

/// Associa um perfil e a confiança aos segmentos de um cluster.
pub fn set_speaker_for_cluster(
    db: &Db,
    meeting_id: &str,
    cluster: i64,
    speaker_id: &str,
    confidence: f64,
) -> AppResult<()> {
    db.with(|conn| {
        conn.execute(
            "UPDATE transcript_segment
             SET speaker_id = ?3, confidence = ?4
             WHERE meeting_id = ?1 AND cluster = ?2",
            (meeting_id, cluster, speaker_id, confidence),
        )?;
        Ok(())
    })
}

/// Atualiza a identificação de cada cluster. `matches` traz (cluster, perfil, confiança).
pub fn set_speaker_matches(
    db: &Db,
    meeting_id: &str,
    matches: &[(i64, Option<String>, f64)],
) -> AppResult<()> {
    db.with(|conn| {
        let tx = conn.unchecked_transaction()?;
        let mut stmt = tx.prepare(
            "UPDATE transcript_segment
             SET speaker_id = ?3, confidence = ?4
             WHERE meeting_id = ?1 AND cluster = ?2",
        )?;
        for (cluster, speaker_id, confidence) in matches {
            stmt.execute((meeting_id, cluster, speaker_id, confidence))?;
        }
        drop(stmt);
        tx.commit()?;
        Ok(())
    })
}

/// Atualiza o cluster de voz de cada segmento, na ordem de `list`.
/// `clusters[i]` corresponde ao i-esimo segmento ordenado por `start_secs`.
pub fn set_clusters(db: &Db, meeting_id: &str, clusters: &[Option<i64>]) -> AppResult<()> {
    db.with(|conn| {
        let tx = conn.unchecked_transaction()?;
        let ids: Vec<String> = {
            let mut stmt = tx.prepare(
                "SELECT id FROM transcript_segment WHERE meeting_id = ?1 ORDER BY start_secs",
            )?;
            let rows: Vec<String> = stmt
                .query_map([meeting_id], |r| r.get::<_, String>(0))?
                .collect::<Result<_, _>>()?;
            rows
        };
        {
            let mut upd = tx.prepare("UPDATE transcript_segment SET cluster = ?2 WHERE id = ?1")?;
            for (id, cluster) in ids.iter().zip(clusters.iter()) {
                upd.execute((id, cluster))?;
            }
        }
        tx.commit()?;
        Ok(())
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
