# ADR 0002 — Modelos e download

**Decisão:** modelos baixados sob demanda na primeira execução, verificados por
SHA-256, com retomada via HTTP Range. Checksums fixados após o primeiro download
controlado.

## Catálogo inicial (`src-tauri/src/models/catalog.rs`)

| id | uso | origem |
|---|---|---|
| `whisper-small-q5_1` | transcrição (benchmark) | HF `ggerganov/whisper.cpp` |
| `whisper-medium-q5_0` | transcrição (benchmark) | HF `ggerganov/whisper.cpp` |
| `whisper-large-v3-turbo-q5_0` | transcrição (padrão de segurança) | HF `ggerganov/whisper.cpp` |
| `sherpa-segmentation-pyannote` | segmentação de fala | GitHub `k2-fsa/sherpa-onnx` |
| `sherpa-speaker-embedding-3dspeaker` | embedding de voz | GitHub `k2-fsa/sherpa-onnx` |
| `qwen3-4b-instruct-q4_k_m` | resumo / ata | HF `unsloth/Qwen3-4B-Instruct-2507-GGUF` |

## Checksums

Os campos `sha256` estão vazios até a Fase 1. Enquanto vazios, o backend:

1. baixa normalmente;
2. calcula o SHA-256 do arquivo;
3. registra em log (`WARN model=… sha256=…`) e aceita;
4. quando o valor for colado no catálogo, downloads seguintes passam a exigi-lo,
   e um arquivo divergente vira `corrupt` e é apagado.

## Licenças

Todos os modelos acima permitem uso local gratuito. Auditoria formal de licença
antes da distribuição (Fase 5).

## Pendências

- Sherpa distribui alguns modelos em `.tar.bz2`; o manager ainda trata só arquivo
  único. Extração de tarball entra junto com a integração da diarização (Fase 2).
- Avaliar mirror próprio caso HF/GitHub fiquem instáveis.
