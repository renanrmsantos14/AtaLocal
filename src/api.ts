// Camada fina sobre o IPC do Tauri. Toda comunicacao com o backend passa por aqui.
import { invoke } from "@tauri-apps/api/core";
import type {
  SystemDiagnostics,
  ModelInfo,
  AppSettings,
  RecordingState,
  Meeting,
  TranscriptSegment,
  MeetingSummary,
  StoredActionItem,
  WhisperOption,
  SpeakerProfile,
} from "./types";

export const api = {
  diagnostics: {
    run: () => invoke<SystemDiagnostics>("run_diagnostics"),
  },

  logs: {
    get: () => invoke<string>("get_logs"),
    info: () => invoke<{ bytes: number; max_bytes: number }>("get_log_info"),
  },

  speakers: {
    list: () => invoke<SpeakerProfile[]>("list_speaker_profiles"),
    enrollFromMeeting: (meetingId: string, cluster: number, name: string) =>
      invoke<SpeakerProfile>("enroll_speaker_from_meeting", {
        meetingId,
        cluster,
        name,
      }),
  },

  models: {
    list: () => invoke<ModelInfo[]>("list_models"),
    whisperOptions: () => invoke<WhisperOption[]>("whisper_options"),
    download: (modelId: string) =>
      invoke<void>("download_model", { modelId }),
    cancelDownload: (modelId: string) =>
      invoke<void>("cancel_model_download", { modelId }),
    verify: (modelId: string) =>
      invoke<ModelInfo>("verify_model", { modelId }),
    remove: (modelId: string) =>
      invoke<void>("remove_model", { modelId }),
  },

  settings: {
    get: () => invoke<AppSettings>("get_settings"),
    update: (patch: Partial<AppSettings>) =>
      invoke<AppSettings>("update_settings", { patch }),
  },

  recording: {
    start: (title: string, device: string | null) =>
      invoke<Meeting>("start_recording", { title, device }),
    stop: () => invoke<string>("stop_recording"),
    cancel: () => invoke<void>("cancel_recording"),
    state: () => invoke<RecordingState>("recording_state"),
  },

  meetings: {
    list: () => invoke<Meeting[]>("list_meetings"),
    get: (meetingId: string) => invoke<Meeting>("get_meeting", { meetingId }),
    delete: (meetingId: string) =>
      invoke<void>("delete_meeting", { meetingId }),
    segments: (meetingId: string) =>
      invoke<TranscriptSegment[]>("list_segments", { meetingId }),
    summary: (meetingId: string) =>
      invoke<MeetingSummary | null>("get_summary", { meetingId }),
    actions: (meetingId: string) =>
      invoke<StoredActionItem[]>("list_actions", { meetingId }),
    process: (meetingId: string) =>
      invoke<void>("process_meeting", { meetingId }),
  },
};

// Nomes de eventos emitidos pelo backend.
export const events = {
  downloadProgress: "model://download-progress",
  pipelineProgress: "pipeline://progress",
} as const;

export interface PipelineProgress {
  meeting_id: string;
  stage: string;
  progress: number;
  message: string;
}
