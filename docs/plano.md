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

### Transcrição  (FEITO)

- `src-tauri/src/transcribe/mod.rs`: `Transcriber` sobre `whisper-rs` 0.16,
  backend CPU, idioma pt fixo, `no_speech_thold` p/ VAD de bordas, timestamps
  por segmento, callback de progresso. Lê áudio de WAV ou FLAC mono 16 kHz.
- `src-tauri/src/pipeline/mod.rs`: runner retomável. Lê `Stage` do banco,
  executa, avança. `finalizing → transcribing → diarizing → identifying →
  summarizing → completed`. Emite `pipeline://progress`.
- `src-tauri/src/db/segments.rs`: repo de `transcript_segment`, `replace_all`
  idempotente (retranscrever é seguro).
- `stop_recording` dispara o pipeline automaticamente numa thread.
- Comandos: `list_segments`, `process_meeting` (retomar/reprocessar).
- UI: `ResultView` com abas Transcrição / Ata / Tarefas, progresso ao vivo,
  "tentar novamente" em falha.
- **Validado com gravação real**: whisper transcreveu pt-BR corretamente
  ("Oi, tudo bem? ... teste ... de transcrição ...").
- Modelos: catálogo com URLs corrigidas (tag do Sherpa tem typo
  "recongition"), checksums fixados p/ small, large-v3-turbo e segmentação;
  extração de `.tar.bz2` no downloader (`tar` + `bzip2`).

### Ambiente adicionado

- LLVM 22 (`winget install LLVM.LLVM`) — libclang para o bindgen do
  whisper-rs-sys. CMake + LLVM no PATH do usuário. `LIBCLANG_PATH` em
  `src-tauri/.cargo/config.toml`.

### Diarização  (FEITO)

- `src-tauri/src/diarize/mod.rs`: `Diarizer` sobre `sherpa-rs` 0.6
  (segmentação pyannote + embedding campplus). `num_clusters` = participantes
  conhecidos (settings). `assign_clusters()` liga cada segmento de transcrição
  ao cluster de voz de maior sobreposição temporal (min_overlap 0.2s).
- `db/segments.rs::set_clusters`: grava o cluster por segmento.
- Pipeline: etapa `diarizing` roda o Sherpa; falha nela não invalida a
  transcrição (segue sem separação e registra o motivo).
- Modelos: `sherpa-segmentation-pyannote` (.tar.bz2, extraído p/ `model.onnx`)
  e `sherpa-speaker-embedding-campplus` (checksum fixado). O typo do upstream
  na tag é "speaker-recongition-models".
- UI: `ResultView` mostra "Voz 1/2/3" com cor por cluster.
- **Validado**: teste de integração com Sherpa real + áudio de 2 locutores
  detectou exatamente 2 vozes. 8/8 testes verdes.

### Pendente na Fase 2

- [ ] Identificação de vozes — `identifying` (cadastro de perfis + associação
      cluster→pessoa; hoje é stub). Depende da Fase 3.
- [ ] Resumo (llama.cpp + Qwen3) — `summarizing` (hoje é stub)
- [ ] Bug: reunião gravada antes do modelo fica `failed`; o runner deveria
      re-tentar sozinho quando o modelo aparece (hoje precisa do botão)

## Fase 3 — Perfis de voz  (PENDENTE)
## Fase 4 — Experiência principal  (PENDENTE)
## Fase 5 — Robustez e distribuição  (PENDENTE)
