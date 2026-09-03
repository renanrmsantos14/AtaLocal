// Camada fina sobre o IPC do Tauri. Toda comunicacao com o backend passa por aqui.
import { invoke } from "@tauri-apps/api/core";
import type {
  SystemDiagnostics,
  ModelInfo,
  AppSettings,
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
};

// Nomes de eventos emitidos pelo backend.
export const events = {
  downloadProgress: "model://download-progress",
} as const;
