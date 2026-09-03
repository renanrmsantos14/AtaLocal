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
- [ ] Fixar checksums definitivos dos modelos após primeiro download
- [ ] Benchmark real no Inspiron 15 3530 (tempo, memória, qualidade)
- [ ] Escolher modelo whisper definitivo (padrão de segurança: `large-v3-turbo-q5_0`)

## Fase 2 — Fluxo vertical mínimo  (PENDENTE)

`gravar → transcrever → separar vozes → identificar → resumir → exibir`

Estados persistidos: `recording, finalizing, transcribing, diarizing,
identifying, summarizing, completed, failed, cancelled`.

## Fase 3 — Perfis de voz  (PENDENTE)
## Fase 4 — Experiência principal  (PENDENTE)
## Fase 5 — Robustez e distribuição  (PENDENTE)
