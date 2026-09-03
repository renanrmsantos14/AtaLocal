//! Conversao WAV PCM16 -> FLAC (sem perda) usando `flacenc` (Rust puro).
//! Chamada na etapa `finalizing`; o WAV de origem e apagado em seguida.

use std::path::Path;

use flacenc::component::BitRepr;
use flacenc::error::Verify;

use crate::error::{AppError, AppResult};

/// Le um WAV PCM16 minimalista (cabecalho de 44 bytes, chunk `data` unico).
fn read_wav_pcm16(path: &Path) -> AppResult<(u32, u16, Vec<i32>)> {
    let bytes = std::fs::read(path)?;
    if bytes.len() < 44 || &bytes[0..4] != b"RIFF" || &bytes[8..12] != b"WAVE" {
        return Err(AppError::Audio("wav invalido".into()));
    }
    let channels = u16::from_le_bytes([bytes[22], bytes[23]]);
    let sample_rate = u32::from_le_bytes([bytes[24], bytes[25], bytes[26], bytes[27]]);
    let bits = u16::from_le_bytes([bytes[34], bytes[35]]);
    if bits != 16 {
        return Err(AppError::Audio(format!("esperado PCM16, veio {bits} bits")));
    }

    // Procura o chunk "data" a partir do byte 12.
    let mut pos = 12usize;
    let mut data_range = None;
    while pos + 8 <= bytes.len() {
        let id = &bytes[pos..pos + 4];
        let size = u32::from_le_bytes([
            bytes[pos + 4],
            bytes[pos + 5],
            bytes[pos + 6],
            bytes[pos + 7],
        ]) as usize;
        let start = pos + 8;
        if id == b"data" {
            let end = (start + size).min(bytes.len());
            data_range = Some((start, end));
            break;
        }
        pos = start + size + (size & 1);
    }
    let (start, end) = data_range.ok_or_else(|| AppError::Audio("wav sem chunk data".into()))?;

    let samples: Vec<i32> = bytes[start..end]
        .chunks_exact(2)
        .map(|b| i16::from_le_bytes([b[0], b[1]]) as i32)
        .collect();

    Ok((sample_rate, channels, samples))
}

/// Converte `src` (WAV PCM16) em `dst` (FLAC). Retorna o tamanho do FLAC.
pub fn wav_to_flac(src: &Path, dst: &Path) -> AppResult<u64> {
    let (sample_rate, channels, samples) = read_wav_pcm16(src)?;

    let config = flacenc::config::Encoder::default()
        .into_verified()
        .map_err(|e| AppError::Audio(format!("config flac invalida: {e:?}")))?;

    let source = flacenc::source::MemSource::from_samples(
        &samples,
        channels as usize,
        16,
        sample_rate as usize,
    );

    let stream = flacenc::encode_with_fixed_block_size(&config, source, config.block_size)
        .map_err(|e| AppError::Audio(format!("falha ao codificar flac: {e:?}")))?;

    let mut sink = flacenc::bitsink::ByteSink::new();
    stream
        .write(&mut sink)
        .map_err(|e| AppError::Audio(format!("falha ao serializar flac: {e:?}")))?;

    std::fs::write(dst, sink.as_slice())?;
    Ok(std::fs::metadata(dst)?.len())
}
