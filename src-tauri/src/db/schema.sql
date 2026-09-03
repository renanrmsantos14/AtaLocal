-- AtaLocal — schema local (SQLite). Segmentos sao a fonte primaria;
-- ata e tarefas sao resultados derivados e regeneraveis.

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS speaker_profile (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    color        TEXT NOT NULL,
    quality      REAL NOT NULL DEFAULT 0,
    sample_count INTEGER NOT NULL DEFAULT 0,
    -- media dos embeddings de voz, serializada (f32 little-endian).
    embedding    BLOB,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meeting (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL,
    started_at    TEXT NOT NULL,
    ended_at      TEXT,
    duration_secs REAL NOT NULL DEFAULT 0,
    audio_path    TEXT,
    stage         TEXT NOT NULL DEFAULT 'recording',
    error         TEXT,
    created_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meeting_started_at ON meeting(started_at DESC);

CREATE TABLE IF NOT EXISTS transcript_segment (
    id         TEXT PRIMARY KEY,
    meeting_id TEXT NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    start_secs REAL NOT NULL,
    end_secs   REAL NOT NULL,
    text       TEXT NOT NULL,
    cluster    INTEGER,
    speaker_id TEXT REFERENCES speaker_profile(id) ON DELETE SET NULL,
    confidence REAL NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_segment_meeting ON transcript_segment(meeting_id, start_secs);

CREATE TABLE IF NOT EXISTS meeting_summary (
    meeting_id        TEXT PRIMARY KEY REFERENCES meeting(id) ON DELETE CASCADE,
    executive_summary TEXT NOT NULL DEFAULT '',
    -- listas e entradas estruturadas guardadas como JSON.
    topics            TEXT NOT NULL DEFAULT '[]',
    decisions         TEXT NOT NULL DEFAULT '[]',
    pending           TEXT NOT NULL DEFAULT '[]',
    divergences       TEXT NOT NULL DEFAULT '[]',
    next_steps        TEXT NOT NULL DEFAULT '[]',
    generated_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS action_item (
    id                 TEXT PRIMARY KEY,
    meeting_id         TEXT NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    description        TEXT NOT NULL,
    assignee           TEXT,
    due                TEXT,
    status             TEXT NOT NULL DEFAULT 'open',
    source_segment_ids TEXT NOT NULL DEFAULT '[]'
);

CREATE INDEX IF NOT EXISTS idx_action_meeting ON action_item(meeting_id);

CREATE TABLE IF NOT EXISTS processing_job (
    meeting_id TEXT PRIMARY KEY REFERENCES meeting(id) ON DELETE CASCADE,
    stage      TEXT NOT NULL,
    progress   REAL NOT NULL DEFAULT 0,
    model      TEXT,
    checkpoint TEXT,
    updated_at TEXT NOT NULL
);

-- Chave-valor unica para configuracoes do app.
CREATE TABLE IF NOT EXISTS app_settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Estado de download por modelo (bytes ja baixados = retomada).
CREATE TABLE IF NOT EXISTS model_state (
    id               TEXT PRIMARY KEY,
    status           TEXT NOT NULL DEFAULT 'not_downloaded',
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    error            TEXT,
    updated_at       TEXT NOT NULL
);
