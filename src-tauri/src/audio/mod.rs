//! Captura de audio. Fase 2: gravacao por WASAPI com salvamento incremental,
//! copia mono 16 kHz PCM16 e medidor de volume.

pub mod flac;
pub mod recorder;
pub mod resample;
pub mod wav;

/// Taxa de amostragem alvo para o pipeline de processamento (VAD + whisper).
pub const TARGET_SAMPLE_RATE: u32 = 16_000;
