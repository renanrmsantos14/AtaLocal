# ADR 0003 — Captura e armazenamento de áudio

## Decisões

### Captura em 3 threads

`cpal::Stream` **não é `Send` no Windows** (WASAPI). Não dá para movê-lo para
uma thread de escrita. Arranjo:

1. **Thread de captura** cria o stream, mantém-no vivo, dorme em loop de 100 ms
   checando o sinal de parada. O stream nunca sai desta thread.
2. **Callback de áudio** (tempo real) só empurra `Vec<f32>` num `mpsc::channel`.
3. **Thread de escrita** consome o canal, grava os arquivos e calcula RMS/pico.

A config do dispositivo é devolvida da thread de captura para a principal por um
canal, antes de abrir os arquivos — assim erro de microfone aparece cedo e claro.

### WAV durante a captura, FLAC ao finalizar

Gravar direto em FLAC exigiria um encoder streaming e arriscaria um arquivo
inválido se o processo cair. Em vez disso:

- durante a gravação: **WAV PCM16 incremental**, cabeçalho reescrito e `sync_data`
  a cada 3 s → o arquivo é sempre reproduzível mesmo após um crash;
- na etapa `finalizing`: converte para **FLAC** (`flacenc`, Rust puro, sem
  dependência de sistema) e apaga o WAV. Se a conversão falhar, mantém o WAV.

FLAC é sem perda: ~50–60% do tamanho do WAV, sem impacto em transcrição/diarização.

### Dois arquivos por reunião

- `<id>-original.flac` — taxa/canais nativos do microfone, preservado para
  reprocessamento e reprodução.
- `<id>-16k.flac` — mono, 16 kHz, PCM16 → FLAC. Entrada do VAD + whisper.

Downmix por média dos canais + reamostragem linear com fase contínua entre
blocos (`resample.rs`). Suficiente para fala; a qualidade de captura fica no
arquivo original.

## Alternativas descartadas

- **flacenc streaming**: a API de bloco fixo é mais simples e a conversão
  pós-gravação já cabe na etapa `finalizing`.
- **Opus para o original**: economiza mais, mas com perda. Fica como opção
  futura nas configurações ("modo compacto"), não como padrão.
- **Manter WAV**: ~2 GB por reunião de 3 h no original — inviável com retenção.
