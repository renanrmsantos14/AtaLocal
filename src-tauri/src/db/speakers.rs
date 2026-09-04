use serde::Serialize;

use crate::db::Db;
use crate::error::{AppError, AppResult};

#[derive(Debug, Clone, Serialize)]
pub struct SpeakerProfile {
    pub id: String,
    pub name: String,
    pub color: String,
    pub quality: f64,
    pub sample_count: i64,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone)]
pub struct SpeakerEmbedding {
    pub id: String,
    pub embedding: Vec<f32>,
}

const COLORS: [&str; 6] = [
    "#4c8dff", "#3fb950", "#d29922", "#f85149", "#a371f7", "#79c0ff",
];

fn blob_to_embedding(blob: Vec<u8>) -> AppResult<Vec<f32>> {
    if blob.len() % 4 != 0 {
        return Err(AppError::Other("impressao de voz corrompida".into()));
    }
    Ok(blob
        .chunks_exact(4)
        .map(|bytes| f32::from_le_bytes(bytes.try_into().expect("chunk de 4 bytes")))
        .collect())
}

fn embedding_to_blob(embedding: &[f32]) -> Vec<u8> {
    embedding
        .iter()
        .flat_map(|value| value.to_le_bytes())
        .collect()
}

fn row_to_profile(r: &rusqlite::Row) -> rusqlite::Result<SpeakerProfile> {
    Ok(SpeakerProfile {
        id: r.get(0)?,
        name: r.get(1)?,
        color: r.get(2)?,
        quality: r.get(3)?,
        sample_count: r.get(4)?,
        created_at: r.get(5)?,
        updated_at: r.get(6)?,
    })
}

pub fn list(db: &Db) -> AppResult<Vec<SpeakerProfile>> {
    db.with(|conn| {
        let mut stmt = conn.prepare(
            "SELECT id, name, color, quality, sample_count, created_at, updated_at
             FROM speaker_profile ORDER BY name COLLATE NOCASE",
        )?;
        let rows = stmt
            .query_map([], row_to_profile)?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    })
}

pub fn list_embeddings(db: &Db) -> AppResult<Vec<SpeakerEmbedding>> {
    db.with(|conn| {
        let mut stmt = conn.prepare(
            "SELECT id, name, embedding FROM speaker_profile
             WHERE embedding IS NOT NULL ORDER BY name COLLATE NOCASE",
        )?;
        let rows = stmt
            .query_map([], |r| {
                let blob: Vec<u8> = r.get(2)?;
                Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?, blob))
            })?
            .collect::<Result<Vec<_>, _>>()?;

        rows.into_iter()
            .map(|(id, _name, blob)| {
                Ok(SpeakerEmbedding {
                    id,
                    embedding: blob_to_embedding(blob)?,
                })
            })
            .collect()
    })
}

pub fn upsert(db: &Db, name: &str, embedding: &[f32]) -> AppResult<SpeakerProfile> {
    let name = name.trim();
    if name.is_empty() || name.chars().count() > 80 || name.chars().any(char::is_control) {
        return Err(AppError::Other(
            "informe um nome de 1 a 80 caracteres".into(),
        ));
    }
    if embedding.is_empty() {
        return Err(AppError::Other("a impressao de voz esta vazia".into()));
    }

    let now = chrono::Utc::now().to_rfc3339();
    let new_blob = embedding_to_blob(embedding);
    db.with(|conn| {
        let existing: Option<(String, Option<Vec<u8>>, i64, String)> = conn
            .query_row(
                "SELECT id, embedding, sample_count, color FROM speaker_profile
                 WHERE lower(name) = lower(?1) LIMIT 1",
                [name],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
            )
            .ok();

        let id = existing
            .as_ref()
            .map(|(id, _, _, _)| id.clone())
            .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
        let (blob, sample_count) = match existing.as_ref() {
            Some((_, Some(old_blob), count, _)) => {
                let old = blob_to_embedding(old_blob.clone())?;
                if old.len() == embedding.len() {
                    let count = (*count).max(1) as f32;
                    let averaged = old
                        .iter()
                        .zip(embedding)
                        .map(|(a, b)| (a * count + b) / (count + 1.0))
                        .collect::<Vec<_>>();
                    (embedding_to_blob(&averaged), count as i64 + 1)
                } else {
                    (new_blob.clone(), 1)
                }
            }
            Some((_, None, count, _)) => (new_blob.clone(), (*count).max(0) + 1),
            None => (new_blob.clone(), 1),
        };
        let color = existing
            .as_ref()
            .map(|(_, _, _, color)| color.clone())
            .unwrap_or_else(|| {
                let count: i64 = conn
                    .query_row("SELECT COUNT(*) FROM speaker_profile", [], |r| r.get(0))
                    .unwrap_or(0);
                COLORS[count as usize % COLORS.len()].to_string()
            });

        conn.execute(
            "INSERT INTO speaker_profile
               (id, name, color, quality, sample_count, embedding, created_at, updated_at)
             VALUES (?1, ?2, ?3, 1, ?4, ?5, ?6, ?6)
             ON CONFLICT(id) DO UPDATE SET
               name = excluded.name, quality = excluded.quality,
               sample_count = excluded.sample_count, embedding = excluded.embedding,
               updated_at = excluded.updated_at",
            (&id, name, &color, sample_count, blob, &now),
        )?;
        conn.query_row(
            "SELECT id, name, color, quality, sample_count, created_at, updated_at
             FROM speaker_profile WHERE id = ?1",
            [&id],
            row_to_profile,
        )
        .map_err(Into::into)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn converte_embedding_para_blob_e_volta() {
        let values = [0.25_f32, -1.5, 3.0];
        assert_eq!(
            blob_to_embedding(embedding_to_blob(&values)).unwrap(),
            values
        );
    }
}
