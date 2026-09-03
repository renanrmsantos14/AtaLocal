//! Transcricao com whisper.cpp (via `whisper-rs`), backend CPU, idioma pt.
//! Le o WAV/FLAC 16 kHz mono da reuniao e produz segmentos com timestamps.

use std::path::Path;

use whisper_rs::{
    FullParams, SamplingStrategy, WhisperContext, WhisperContextParameters,
};

use crate::error::{AppError, AppResult};

/// Um trecho transcrito, alinhado no tempo da reuniao.
#[derive(Debug, Clone)]
pub struct RawSegment {
    pub start_secs: f64,
    pub end_secs: f64,
    pub text: String,
}

pub struct Transcriber {
    ctx: WhisperContext,
}

impl Transcriber {
    /// Carrega o modelo GGML/GGUF do whisper a partir do disco.
    pub fn load(model_path: &Path) -> AppResult<Self> {
        if !model_path.exists() {
            return Err(AppError::Model(format!(
                "modelo de transcricao ausente: {}",
                model_path.display()
            )));
        }
        let model_str = model_path.to_string_lossy().into_owned();
        let ctx = WhisperContext::new_with_params(
            &model_str,
            WhisperContextParameters::default(),
        )
        .map_err(|e| AppError::Other(format!("falha ao carregar whisper: {e}")))?;
        Ok(Self { ctx })
    }

    /// Transcreve `samples` (mono f32, 16 kHz). `progress` recebe 0..1.
    pub fn run<F>(&self, samples: &[f32], progress: F) -> AppResult<Vec<RawSegment>>
    where
        F: Fn(f32) + Send + 'static,
    {
        let mut state = self
            .ctx
            .create_state()
            .map_err(|e| AppError::Other(format!("estado do whisper: {e}")))?;

        let mut params = FullParams::new(SamplingStrategy::Greedy { best_of: 1 });
        params.set_language(Some("pt"));
        params.set_translate(false);
        params.set_print_special(false);
        params.set_print_progress(false);
        params.set_print_realtime(false);
        params.set_print_timestamps(false);
        params.set_token_timestamps(true);
        // whisper.cpp tem VAD interno de silencio nas bordas via no_speech_thold.
        params.set_no_speech_thold(0.6);
        params.set_suppress_blank(true);
        let threads = std::thread::available_parallelism()
            .map(|n| n.get() as i32)
            .unwrap_or(4);
        params.set_n_threads(threads.max(1));

        params.set_progress_callback_safe(move |p| progress(p as f32 / 100.0));

        state
            .full(params, samples)
            .map_err(|e| AppError::Other(format!("transcricao falhou: {e}")))?;

        let n = state.full_n_segments();
        let mut out = Vec::with_capacity(n.max(0) as usize);
        for i in 0..n {
            let Some(seg) = state.get_segment(i) else {
                continue;
            };
            let text = seg
                .to_str_lossy()
                .map_err(|e| AppError::Other(e.to_string()))?
                .trim()
                .to_string();
            if text.is_empty() {
                continue;
            }
            // Timestamps do whisper vem em centesimos de segundo.
            out.push(RawSegment {
                start_secs: seg.start_timestamp() as f64 / 100.0,
                end_secs: seg.end_timestamp() as f64 / 100.0,
                text,
            });
        }
        Ok(out)
    }
}

/// Le um WAV PCM16 mono OU um FLAC mono e devolve amostras f32 normalizadas.
/// A taxa e assumida 16 kHz (garantida pela etapa de captura).
pub fn load_mono_16k(path: &Path) -> AppResult<Vec<f32>> {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_lowercase();
    match ext.as_str() {
        "wav" => read_wav_pcm16_mono(path),
        "flac" => read_flac_mono(path),
        other => Err(AppError::Audio(format!("formato nao suportado: {other}"))),
    }
}

fn read_wav_pcm16_mono(path: &Path) -> AppResult<Vec<f32>> {
    let bytes = std::fs::read(path)?;
    if bytes.len() < 44 || &bytes[0..4] != b"RIFF" {
        return Err(AppError::Audio("wav invalido".into()));
    }
    let channels = u16::from_le_bytes([bytes[22], bytes[23]]).max(1) as usize;
    let mut pos = 12usize;
    let (mut start, mut end) = (0usize, 0usize);
    while pos + 8 <= bytes.len() {
        let id = &bytes[pos..pos + 4];
        let size =
            u32::from_le_bytes([bytes[pos + 4], bytes[pos + 5], bytes[pos + 6], bytes[pos + 7]])
                as usize;
        if id == b"data" {
            start = pos + 8;
            end = (start + size).min(bytes.len());
            break;
        }
        pos = pos + 8 + size + (size & 1);
    }
    if end == 0 {
        return Err(AppError::Audio("wav sem chunk data".into()));
    }
    let samples: Vec<f32> = bytes[start..end]
        .chunks_exact(2)
        .map(|b| i16::from_le_bytes([b[0], b[1]]) as f32 / i16::MAX as f32)
        .collect();
    if channels == 1 {
        Ok(samples)
    } else {
        Ok(samples
            .chunks(channels)
            .map(|c| c.iter().sum::<f32>() / channels as f32)
            .collect())
    }
}

fn read_flac_mono(path: &Path) -> AppResult<Vec<f32>> {
    let mut reader = claxon::FlacReader::open(path)
        .map_err(|e| AppError::Audio(format!("flac invalido: {e}")))?;
    let info = reader.streaminfo();
    let ch = info.channels.max(1) as usize;
    let scale = 1.0f32 / (1i64 << (info.bits_per_sample - 1)) as f32;

    let mut acc: Vec<f32> = Vec::new();
    let mut frame = Vec::new();
    let mut idx = 0usize;
    for sample in reader.samples() {
        let s = sample.map_err(|e| AppError::Audio(e.to_string()))?;
        frame.push(s as f32 * scale);
        idx += 1;
        if idx % ch == 0 {
            acc.push(frame.iter().sum::<f32>() / ch as f32);
            frame.clear();
        }
    }
    Ok(acc)
}
