# ADR 0005 — Diarização como subprocesso (revisa ADR 0004)

## Problema

`whisper-rs` e `sherpa-rs` compilam `whisper.cpp` e `sherpa-onnx` como
bibliotecas C++ estáticas. Linkadas no **mesmo executável**, o whisper.cpp
**aborta** (`STATUS_BREAKPOINT 0x80000003`) logo no `whisper_model_load`, antes
de ler o modelo.

Comprovação: removendo `sherpa-rs` do `Cargo.toml`, a mesma transcrição, mesmo
modelo, mesmo áudio → `test result: ok`. Com `sherpa-rs` presente → abort.

Causa provável: conflito de runtime C++ / símbolos duplicados (sherpa-onnx traz
o onnxruntime; whisper.cpp traz o ggml; ambos com suas próprias cópias de libs
de base). Tentativas de alinhar o CRT (`SHERPA_STATIC_CRT=0`) não recompilaram o
cmake de forma confiável e o esforço não se justificou.

## Decisão

A diarização executa o **`sherpa-onnx-offline-speaker-diarization.exe`**
oficial (build `win-x64-shared-MD-Release-no-tts`, v1.13.7) como **subprocesso**.
`whisper.cpp` e (futuro) `llama.cpp` continuam como bindings compilados —
nenhum deles conflita entre si.

Um processo separado dá isolamento total das bibliotecas C++, elimina ~10 min
de build do sherpa-onnx via cmake, e o `.exe` já vem assinado pelo upstream.

## Implementação

- `src/diarize/mod.rs`: monta a linha de comando
  (`--segmentation.pyannote-model`, `--embedding.model`,
  `--clustering.num-clusters=<participantes>`), executa, e faz parse das linhas
  `INICIO -- FIM speaker_NN` do stdout/stderr.
- Prepende `bin/` e `lib/` do pacote ao `PATH` do processo filho (as DLLs
  `onnxruntime.dll`, `sherpa-onnx-c-api.dll` vivem lá).
- O pipeline escreve um WAV mono 16 kHz temporário do áudio da reunião como
  entrada do exe (ele não lê FLAC), e apaga depois.
- Catálogo: `sherpa-onnx-bin` (`ModelKind::Tool`), baixado e extraído como os
  demais `.tar.bz2` para `models/sherpa-onnx-bin/`.

## Consequências

- Instalador ganha ~19 MB (o pacote do sherpa).
- Diarização de 34 s de áudio: ~7–12 s (RTF ~0.35).
- Falha do subprocesso não invalida a transcrição (o pipeline segue e registra).
- `ModelKind::Embedding` (campplus) e `Diarization` (pyannote) continuam iguais
  — só a *execução* mudou de FFI para subprocesso.
