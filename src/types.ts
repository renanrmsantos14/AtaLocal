// Contratos de dados compartilhados entre o frontend e o backend Rust.
// Os nomes de campo usam snake_case para espelhar exatamente o serde do Rust.

export type ProcessingStage =
  | "recording"
  | "finalizing"
  | "transcribing"
  | "diarizing"
  | "identifying"
  | "summarizing"
  | "completed"
  | "failed"
  | "cancelled";

export interface SpeakerProfile {
  id: string;
  name: string;
  color: string;
  /** Qualidade média do perfil de voz, 0..1. */
  quality: number;
  sample_count: number;
  created_at: string;
  updated_at: string;
}

export interface Meeting {
  id: string;
  title: string;
  started_at: string;
  ended_at: string | null;
  /** Duração em segundos. */
  duration_secs: number;
  /** Caminho do áudio original preservado. */
  audio_path: string | null;
  stage: ProcessingStage;
  /** Mensagem de erro recuperável, se houver. */
  error: string | null;
  created_at: string;
}

export interface TranscriptSegment {
  id: string;
  meeting_id: string;
  /** Início em segundos a partir do começo da reunião. */
  start_secs: number;
  end_secs: number;
  text: string;
  /** Cluster de voz atribuído pela diarização (0..n-1) ou null. */
  cluster: number | null;
  /** Perfil de pessoa associado, ou null se "Não identificado". */
  speaker_id: string | null;
  /** Confiança da atribuição de voz, 0..1. */
  confidence: number;
}

export interface ActionItem {
  id: string;
  meeting_id: string;
  description: string;
  /** "Não informado" quando ausente. */
  assignee: string | null;
  due: string | null;
  status: "open" | "done" | "cancelled";
  /** IDs de TranscriptSegment que originaram o item. */
  source_segment_ids: string[];
}

export interface MeetingSummary {
  meeting_id: string;
  executive_summary: string;
  topics: string[];
  decisions: SummaryEntry[];
  pending: SummaryEntry[];
  divergences: SummaryEntry[];
  next_steps: string[];
  generated_at: string;
}

export interface SummaryEntry {
  text: string;
  /** Referência de horário "HH:MM:SS" no áudio. */
  timestamp: string | null;
}

export interface StoredActionItem {
  id: string;
  meeting_id: string;
  description: string;
  assignee: string | null;
  due: string | null;
  status: string;
}

export interface ProcessingJob {
  meeting_id: string;
  stage: ProcessingStage;
  /** Progresso da etapa atual, 0..1. */
  progress: number;
  /** Modelo em uso na etapa atual. */
  model: string | null;
  /** Checkpoint serializado para retomada. */
  checkpoint: string | null;
  updated_at: string;
}

export interface AppSettings {
  input_device: string | null;
  whisper_model: string;
  retention_days: number | null;
  data_dir: string;
  models_dir: string;
  low_power_mode: boolean;
  participant_count: number;
}

// ---- Diagnóstico ----

export interface AudioDevice {
  name: string;
  is_default: boolean;
  default_sample_rate: number;
  channels: number;
}

export interface SystemDiagnostics {
  cpu_name: string;
  cpu_cores_physical: number;
  cpu_cores_logical: number;
  total_ram_gb: number;
  available_ram_gb: number;
  data_dir_free_gb: number;
  input_devices: AudioDevice[];
  os_version: string;
  /** Verificações agregadas com veredito legível. */
  checks: DiagnosticCheck[];
}

export interface DiagnosticCheck {
  id: string;
  label: string;
  status: "ok" | "warn" | "fail";
  detail: string;
}

// ---- Modelos ----

export type ModelStatus =
  | "not_downloaded"
  | "downloading"
  | "verifying"
  | "ready"
  | "corrupt"
  | "failed";

export interface ModelProfile {
  label: string;
  ram_mb: number;
  secs_per_audio_min: number;
  quality: number;
  note: string;
}

export interface WhisperOption {
  id: string;
  filename: string;
  size_bytes: number;
  profile: ModelProfile;
  recommended: boolean;
  warning: string | null;
}

export interface ModelInfo {
  id: string;
  kind: "whisper" | "diarization" | "embedding" | "llm" | "tool";
  filename: string;
  url: string;
  sha256: string;
  size_bytes: number;
  status: ModelStatus;
  /** Bytes já baixados (para retomada e barra de progresso). */
  downloaded_bytes: number;
  error: string | null;
}

export interface RecordingState {
  meeting_id: string | null;
  recording: boolean;
  level: number;
  peak: number;
  duration_secs: number;
  signal: "ok" | "baixo" | "saturado" | "sem_sinal";
  error: string | null;
}

export interface DownloadProgress {
  model_id: string;
  downloaded_bytes: number;
  total_bytes: number;
  /** Bytes por segundo. */
  speed: number;
  status: ModelStatus;
}
