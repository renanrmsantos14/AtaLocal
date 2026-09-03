# ADR 0001 — Stack base

**Decisão:** Tauri 2 (UI React/TS) + backend Rust + SQLite, sem servidor exposto.

## Contexto

App Windows desktop, offline, sem conta/telemetria. Precisa embarcar binários
nativos pesados (whisper.cpp, sherpa-onnx, llama.cpp) e capturar áudio por WASAPI.

## Alternativas

- **Electron:** bundle grande, sem afinidade natural com FFI nativa, maior uso de RAM.
- **.NET / WPF:** bom no Windows, mas integração com os runtimes C++ e ONNX é
  mais trabalhosa e o ecossistema de crates Rust (cpal, rusqlite, reqwest) cobre
  tudo que precisamos.
- **Tauri 2:** WebView2 já presente no Windows 11, backend Rust compila os
  binários nativos como dependências, IPC interno sem porta aberta.

## Consequências

- Requer toolchain MSVC + CMake na máquina de build.
- Instalador NSIS `currentUser` evita prompt de UAC; SmartScreen ainda aparece
  até haver certificado de assinatura.
- CSP restrita: `connect-src` só `ipc:`. Nenhuma origem remota liberada em runtime.
