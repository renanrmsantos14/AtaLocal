// Camada fina sobre o IPC do Tauri. Toda comunicacao com o backend passa por aqui.
import { invoke } from "@tauri-apps/api/core";
import type {
  SystemDiagnostics,
  ModelInfo,
  AppSettings,
  RecordingState,
  Meeting,
} from "./types";

export const api = {
  diagnostics: {
    run: () => invoke<SystemDiagnostics>("run_diagnostics"),
  },

  models: {
    list: () => invoke<ModelInfo[]>("list_models"),
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
  },
};

// Nomes de eventos emitidos pelo backend.
export const events = {
  downloadProgress: "model://download-progress",
} as const;
