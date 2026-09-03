# Plano AtaLocal

O plano-mestre (objetivo, arquitetura, fases, contratos, validação) está
registrado na conversa de origem. Este arquivo resume o estado de execução.

## Fase 1 — Fundação e benchmark  (EM ANDAMENTO)

- [x] Repositório git independente em `C:\Users\mendo\Desktop\AP\AtaLocal`
- [x] Scaffold Tauri 2 + React + TypeScript + Vite
- [x] Schema SQLite completo (`src-tauri/src/db/schema.sql`)
- [x] Contratos de dados: `src/types.ts` ↔ structs Rust
- [x] Diagnóstico: CPU, RAM, disco, microfones (`src-tauri/src/diagnostics`)
- [x] Gerenciador de download de modelos: progresso, checksum SHA-256, retomada
      via HTTP Range, cancelamento (`src-tauri/src/models`)
- [x] Build compila limpo; app sobe com `npm run tauri dev`
- [x] Smoke tests da Fase 1 passando (`cargo test --test phase1_smoke`)
- [ ] Fixar checksums definitivos dos modelos após primeiro download
- [ ] Benchmark real no Inspiron 15 3530 (tempo, memória, qualidade)
- [ ] Escolher modelo whisper definitivo (padrão de segurança: `large-v3-turbo-q5_0`)

## Fase 2 — Fluxo vertical mínimo  (EM ANDAMENTO)

`gravar → transcrever → separar vozes → identificar → resumir → exibir`

Estados persistidos: `recording, finalizing, transcribing, diarizing,
identifying, summarizing, completed, failed, cancelled` — enum `Stage` em
`src-tauri/src/db/meetings.rs` com `next_happy()` / `is_terminal()`.

### Captura de áudio  (FEITO)

- `src-tauri/src/audio/`:
  - `recorder.rs`: thread de captura (stream cpal, não-Send, fica na thread) +
    callback enxuto + thread de escrita. Nível RMS/pico, detecção de mic removido.
  - `wav.rs`: WAV PCM16 incremental, cabeçalho reescrito a cada flush (3 s).
  - `resample.rs`: downmix mono + reamostragem linear para 16 kHz.
  - `flac.rs`: WAV → FLAC (sem perda, `flacenc` Rust puro) na etapa `finalizing`.
- `src-tauri/src/session.rs`: `SessionManager` — 1 gravação por vez, liga
  recorder ↔ `meeting`, converte para FLAC ao parar, apaga o WAV.
- Comandos: `start_recording`, `stop_recording`, `cancel_recording`,
  `recording_state`, `list_meetings`, `get_meeting`, `delete_meeting`.
- UI: `RecordView` (seleção de mic, medidor de volume ao vivo, aviso de sinal
  baixo/saturado, encerrar/descartar) e `MeetingsView` (histórico + estado).
- Recuperação: reunião presa em `recording` por fechamento abrupto vira
  `failed` recuperável na próxima abertura.
- Testes: `phase2_audio.rs` (WAV incremental, downsampler 48→16k, WAV→FLAC).

### Pendente na Fase 2

- [ ] Gravação real testada de ponta a ponta pelo microfone
- [ ] Transcrição (whisper.cpp) — etapa `transcribing`
- [ ] Diarização (Sherpa-ONNX) — `diarizing`
- [ ] Identificação de vozes — `identifying`
- [ ] Resumo (llama.cpp + Qwen3) — `summarizing`
- [ ] Runner de pipeline retomável ligando as etapas
- [ ] Tela de resultado (abas Ata / Transcrição / Tarefas)

## Fase 3 — Perfis de voz  (PENDENTE)
## Fase 4 — Experiência principal  (PENDENTE)
## Fase 5 — Robustez e distribuição  (PENDENTE)
