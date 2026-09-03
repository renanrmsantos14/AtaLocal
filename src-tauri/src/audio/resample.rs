//! Conversao para o formato de processamento: mono, 16 kHz, i16.
//! Downmix por media dos canais + reamostragem linear. Suficiente para
//! alimentar o VAD e o whisper; qualidade de captura fica no arquivo original.

/// Converte um bloco de amostras f32 intercaladas (`src_channels`, `src_rate`)
/// para mono i16 a `dst_rate`, mantendo a fase entre blocos via `carry`.
pub struct Downsampler {
    src_rate: u32,
    dst_rate: u32,
    src_channels: u16,
    /// Posicao fracionaria acumulada no fluxo de entrada mono.
    pos: f64,
    /// Ultima amostra mono do bloco anterior (para interpolar na borda).
    last: f32,
    primed: bool,
}

impl Downsampler {
    pub fn new(src_rate: u32, src_channels: u16, dst_rate: u32) -> Self {
        Self {
            src_rate,
            dst_rate,
            src_channels: src_channels.max(1),
            pos: 0.0,
            last: 0.0,
            primed: false,
        }
    }

    pub fn process(&mut self, interleaved: &[f32]) -> Vec<i16> {
        let ch = self.src_channels as usize;
        if interleaved.is_empty() || ch == 0 {
            return Vec::new();
        }

        // Downmix para mono.
        let frames = interleaved.len() / ch;
        let mut mono = Vec::with_capacity(frames);
        for f in 0..frames {
            let mut acc = 0.0f32;
            for c in 0..ch {
                acc += interleaved[f * ch + c];
            }
            mono.push(acc / ch as f32);
        }

        if !self.primed {
            self.last = mono[0];
            self.primed = true;
        }

        let ratio = self.src_rate as f64 / self.dst_rate as f64;
        let mut out = Vec::with_capacity(((frames as f64) / ratio) as usize + 2);

        // pos e medido em amostras de entrada, relativo ao inicio deste bloco.
        while self.pos < frames as f64 {
            let i = self.pos.floor() as isize;
            let frac = (self.pos - self.pos.floor()) as f32;
            let a = if i < 0 { self.last } else { mono[i as usize] };
            let b = if (i + 1) < frames as isize {
                mono[(i + 1) as usize]
            } else {
                mono[frames - 1]
            };
            let s = a + (b - a) * frac;
            out.push((s.clamp(-1.0, 1.0) * i16::MAX as f32) as i16);
            self.pos += ratio;
        }
        self.pos -= frames as f64;
        self.last = mono[frames - 1];
        out
    }
}
