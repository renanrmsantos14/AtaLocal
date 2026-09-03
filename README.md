# AtaLocal

Aplicativo Windows para transcrição e ata de reuniões presenciais, **100% local**.
Sem conta, sem assinatura, sem API paga, sem telemetria, sem envio de áudio.

## Fluxo principal

`abrir → gravar → parar → aguardar → revisar`

Grava a reunião por um único microfone, transcreve em português, separa as falas por
voz, identifica pelos nomes das pessoas cadastradas e gera resumo com decisões,
tarefas, responsáveis, prazos e pendências.

## Stack

| Camada | Tecnologia |
|---|---|
| UI | Tauri 2 + React + TypeScript + Vite |
| Backend local | Rust |
| Banco | SQLite (`rusqlite`) |
| Captura de áudio | `cpal` (WASAPI) |
| Transcrição | `whisper.cpp` (CPU) |
| Diarização / voz | Sherpa-ONNX |
| Resumo | `llama.cpp` + Qwen3 4B `Q4_K_M` |

Modelos são baixados sob demanda na primeira execução, com verificação de checksum
SHA-256 e retomada de download.

## Requisitos de build (Windows x64)

- Node 18+ e npm
- Rust (rustup) — toolchain `stable-x86_64-pc-windows-msvc`
- Visual Studio Build Tools 2022 com workload "Desenvolvimento para desktop com C++"
- CMake (para compilar whisper.cpp / llama.cpp / sherpa-onnx)
- WebView2 Runtime (já vem no Windows 11)

## Desenvolvimento

```bash
npm install
npm run tauri dev
```

## Estrutura

```
src/            Frontend React
src-tauri/      Backend Rust + config Tauri
  src/
    main.rs         Bootstrap
    db/             SQLite: schema, migrations, repos
    diagnostics/    CPU, RAM, disco, microfones
    models/         Gerenciador de download de modelos
    audio/          Captura, gravação incremental, resample
    pipeline/       Estados de processamento (Fase 2)
docs/           Plano, decisões de arquitetura (ADR)
scripts/        Utilitários de build e checksum
```

## Estados de processamento de uma reunião

`recording → finalizing → transcribing → diarizing → identifying → summarizing → completed`

Mais `failed`, `cancelled`. Cada etapa é retomável sem repetir as anteriores.

## Privacidade

Áudio, transcrição e perfis de voz permanecem somente neste computador. O app
funciona integralmente sem conexão de rede após os modelos serem baixados.
