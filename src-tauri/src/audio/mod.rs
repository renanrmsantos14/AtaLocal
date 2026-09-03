//! Captura de audio. Fase 1 entrega apenas a enumeracao de dispositivos
//! (em `diagnostics`). A gravacao incremental, a copia mono 16 kHz PCM16 e o
//! medidor de volume entram na Fase 2 — ver docs/plano.md.

// Placeholder para manter o modulo no grafo de compilacao.
#[allow(dead_code)]
pub const TARGET_SAMPLE_RATE: u32 = 16_000;
