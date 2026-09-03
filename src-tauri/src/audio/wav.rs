//! Escritor WAV incremental (PCM). O cabecalho e reescrito a cada flush para
//! que o arquivo seja sempre valido mesmo se o processo cair no meio.

use std::fs::{File, OpenOptions};
use std::io::{BufWriter, Seek, SeekFrom, Write};
use std::path::Path;

use crate::error::AppResult;

pub struct WavWriter {
    inner: BufWriter<File>,
    channels: u16,
    sample_rate: u32,
    bits_per_sample: u16,
    /// Bytes de audio ja gravados (sem contar o cabecalho de 44 bytes).
    data_bytes: u32,
}

impl WavWriter {
    pub fn create(
        path: &Path,
        channels: u16,
        sample_rate: u32,
        bits_per_sample: u16,
    ) -> AppResult<Self> {
        let file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(path)?;
        let mut w = Self {
            inner: BufWriter::new(file),
            channels,
            sample_rate,
            bits_per_sample,
            data_bytes: 0,
        };
        w.write_header()?;
        Ok(w)
    }

    fn write_header(&mut self) -> AppResult<()> {
        let byte_rate =
            self.sample_rate * self.channels as u32 * (self.bits_per_sample as u32 / 8);
        let block_align = self.channels * (self.bits_per_sample / 8);
        let riff_size = 36 + self.data_bytes;

        self.inner.seek(SeekFrom::Start(0))?;
        let h = &mut self.inner;
        h.write_all(b"RIFF")?;
        h.write_all(&riff_size.to_le_bytes())?;
        h.write_all(b"WAVE")?;
        h.write_all(b"fmt ")?;
        h.write_all(&16u32.to_le_bytes())?; // subchunk1 size
        h.write_all(&1u16.to_le_bytes())?; // PCM
        h.write_all(&self.channels.to_le_bytes())?;
        h.write_all(&self.sample_rate.to_le_bytes())?;
        h.write_all(&byte_rate.to_le_bytes())?;
        h.write_all(&block_align.to_le_bytes())?;
        h.write_all(&self.bits_per_sample.to_le_bytes())?;
        h.write_all(b"data")?;
        h.write_all(&self.data_bytes.to_le_bytes())?;
        Ok(())
    }

    /// Anexa amostras i16 intercaladas ao fim do arquivo.
    pub fn write_i16(&mut self, samples: &[i16]) -> AppResult<()> {
        self.inner.seek(SeekFrom::End(0))?;
        let mut buf = Vec::with_capacity(samples.len() * 2);
        for s in samples {
            buf.extend_from_slice(&s.to_le_bytes());
        }
        self.inner.write_all(&buf)?;
        self.data_bytes = self.data_bytes.saturating_add(buf.len() as u32);
        Ok(())
    }

    /// Persiste no disco e atualiza o cabecalho com o tamanho atual.
    pub fn flush(&mut self) -> AppResult<()> {
        self.inner.flush()?;
        self.write_header()?;
        self.inner.flush()?;
        self.inner.get_ref().sync_data()?;
        Ok(())
    }

    pub fn finalize(mut self) -> AppResult<()> {
        self.flush()
    }

    #[allow(dead_code)] // util para diagnostico; a duracao oficial vem do recorder
    pub fn duration_secs(&self) -> f64 {
        let bytes_per_sec =
            self.sample_rate as f64 * self.channels as f64 * (self.bits_per_sample as f64 / 8.0);
        if bytes_per_sec == 0.0 {
            0.0
        } else {
            self.data_bytes as f64 / bytes_per_sec
        }
    }
}
