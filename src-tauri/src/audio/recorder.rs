//! Gravacao nativa. Usa CPAL no desktop e AudioRecord no Android.
//!
//! - **thread de captura**: cria o stream nativo e o mantem vivo,
//!   mantem-no vivo e so acorda para checar o sinal de parada;
//! - **callback de audio**: empurra blocos de f32 para um canal, nada mais;
//! - **thread de escrita**: consome o canal, grava os dois WAV incrementais e
//!   calcula o nivel de volume.
//!
//! Salvamento incremental: flush no disco a cada 3 s. WAV durante a captura
//! (arquivo sempre valido se cair); a conversao para FLAC ocorre em `finalizing`.

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::mpsc::{Receiver, RecvTimeoutError, Sender};
use std::sync::Arc;
use std::time::{Duration, Instant};

#[cfg(not(target_os = "android"))]
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
#[cfg(not(target_os = "android"))]
use cpal::{SampleFormat, StreamConfig};
use parking_lot::Mutex;

use crate::audio::resample::Downsampler;
use crate::audio::wav::WavWriter;
use crate::audio::TARGET_SAMPLE_RATE;
use crate::error::{AppError, AppResult};

#[derive(Clone)]
pub struct RecorderHandle {
    pub(super) inner: Arc<RecorderState>,
}

pub(super) struct RecorderState {
    pub(super) level_milli: AtomicU32,
    pub(super) peak_milli: AtomicU32,
    pub(super) duration_ms: AtomicU32,
    pub(super) stop: AtomicBool,
    pub(super) error: Mutex<Option<String>>,
}

pub struct RecorderStatus {
    pub level: f32,
    pub peak: f32,
    pub duration_secs: f64,
    pub error: Option<String>,
}

impl RecorderHandle {
    pub fn status(&self) -> RecorderStatus {
        RecorderStatus {
            level: self.inner.level_milli.load(Ordering::Relaxed) as f32 / 1000.0,
            peak: self.inner.peak_milli.load(Ordering::Relaxed) as f32 / 1000.0,
            duration_secs: self.inner.duration_ms.load(Ordering::Relaxed) as f64 / 1000.0,
            error: self.inner.error.lock().clone(),
        }
    }

    /// Sinaliza parada sem aguardar (usado em testes e no encerramento de
    /// emergencia). O caminho normal e `RecordingSession::finish`.
    #[allow(dead_code)]
    pub fn stop(&self) {
        self.inner.stop.store(true, Ordering::Relaxed);
    }
}

pub struct RecordingPaths {
    /// Audio original na taxa nativa do microfone (preservado).
    pub original: PathBuf,
    /// Copia mono 16 kHz PCM16 para processamento.
    pub processing: PathBuf,
}

/// Configuracao de audio resolvida do dispositivo, para a thread de escrita.
pub(super) struct ResolvedConfig {
    pub(super) sample_rate: u32,
    pub(super) channels: u16,
}

pub struct RecordingSession {
    handle: RecorderHandle,
    pub(super) capture: Option<std::thread::JoinHandle<()>>,
    pub(super) writer: Option<std::thread::JoinHandle<AppResult<()>>>,
}

impl RecordingSession {
    pub fn handle(&self) -> RecorderHandle {
        self.handle.clone()
    }

    /// Sinaliza parada e aguarda as duas threads. Retorna o resultado da escrita.
    pub fn finish(mut self) -> AppResult<()> {
        self.handle.inner.stop.store(true, Ordering::Relaxed);
        if let Some(c) = self.capture.take() {
            let _ = c.join();
        }
        match self.writer.take() {
            Some(w) => w
                .join()
                .map_err(|_| AppError::Audio("thread de escrita entrou em panico".into()))?,
            None => Ok(()),
        }
    }
}

#[cfg(not(target_os = "android"))]
pub fn start(device_name: Option<&str>, paths: RecordingPaths) -> AppResult<RecordingSession> {
    // Resolve o dispositivo e a config na thread atual so para validar cedo e
    // reportar erro claro; a thread de captura resolve de novo (Device nao e Send).
    let owned_name = resolve_device_name(device_name)?;

    let state = Arc::new(RecorderState {
        level_milli: AtomicU32::new(0),
        peak_milli: AtomicU32::new(0),
        duration_ms: AtomicU32::new(0),
        stop: AtomicBool::new(false),
        error: Mutex::new(None),
    });
    let handle = RecorderHandle {
        inner: state.clone(),
    };

    let (tx, rx) = std::sync::mpsc::channel::<Vec<f32>>();
    // Canal para a thread de captura devolver a config resolvida (ou erro).
    let (cfg_tx, cfg_rx) = std::sync::mpsc::channel::<Result<ResolvedConfig, String>>();

    let capture_state = state.clone();
    let capture = std::thread::Builder::new()
        .name("atalocal-audio-capture".into())
        .spawn(move || capture_loop(owned_name, tx, cfg_tx, capture_state))
        .map_err(|e| AppError::Audio(e.to_string()))?;

    // Espera a config (ou a falha) antes de abrir os arquivos.
    let cfg = match cfg_rx.recv_timeout(Duration::from_secs(5)) {
        Ok(Ok(c)) => c,
        Ok(Err(e)) => {
            let _ = capture.join();
            return Err(AppError::Audio(e));
        }
        Err(_) => {
            state.stop.store(true, Ordering::Relaxed);
            let _ = capture.join();
            return Err(AppError::Audio(
                "tempo esgotado ao abrir o microfone".into(),
            ));
        }
    };

    let writer_state = state.clone();
    let writer = std::thread::Builder::new()
        .name("atalocal-audio-writer".into())
        .spawn(move || writer_loop(rx, paths, cfg, writer_state))
        .map_err(|e| AppError::Audio(e.to_string()))?;

    Ok(RecordingSession {
        handle,
        capture: Some(capture),
        writer: Some(writer),
    })
}

#[cfg(target_os = "android")]
pub fn start(_device_name: Option<&str>, paths: RecordingPaths) -> AppResult<RecordingSession> {
    android::start(paths)
}

#[cfg(not(target_os = "android"))]
fn resolve_device_name(name: Option<&str>) -> AppResult<Option<String>> {
    let host = cpal::default_host();
    match name {
        Some(n) => {
            let exists = host
                .input_devices()
                .map_err(|e| AppError::Audio(e.to_string()))?
                .any(|d| d.name().map(|x| x == n).unwrap_or(false));
            if !exists {
                return Err(AppError::Audio(format!("microfone nao encontrado: {n}")));
            }
            Ok(Some(n.to_string()))
        }
        None => {
            if host.default_input_device().is_none() {
                return Err(AppError::Audio("nenhum microfone padrao".into()));
            }
            Ok(None)
        }
    }
}

#[cfg(not(target_os = "android"))]
fn capture_loop(
    device_name: Option<String>,
    tx: Sender<Vec<f32>>,
    cfg_tx: Sender<Result<ResolvedConfig, String>>,
    state: Arc<RecorderState>,
) {
    let host = cpal::default_host();
    let device = match device_name {
        Some(n) => host
            .input_devices()
            .ok()
            .and_then(|mut it| it.find(|d| d.name().map(|x| x == n).unwrap_or(false))),
        None => host.default_input_device(),
    };
    let Some(device) = device else {
        let _ = cfg_tx.send(Err("microfone indisponivel".into()));
        return;
    };

    let default_cfg = match device.default_input_config() {
        Ok(c) => c,
        Err(e) => {
            let _ = cfg_tx.send(Err(e.to_string()));
            return;
        }
    };
    let sample_format = default_cfg.sample_format();
    let config: StreamConfig = default_cfg.into();
    let _ = cfg_tx.send(Ok(ResolvedConfig {
        sample_rate: config.sample_rate.0,
        channels: config.channels,
    }));

    let err_state = state.clone();
    let err_fn = move |err: cpal::StreamError| {
        *err_state.error.lock() = Some(err.to_string());
        err_state.stop.store(true, Ordering::Relaxed);
    };

    macro_rules! build {
        ($t:ty, $conv:expr) => {{
            let tx = tx.clone();
            device.build_input_stream(
                &config,
                move |data: &[$t], _: &cpal::InputCallbackInfo| {
                    let mut v = Vec::with_capacity(data.len());
                    for &s in data {
                        v.push(($conv)(s));
                    }
                    let _ = tx.send(v);
                },
                err_fn.clone(),
                None,
            )
        }};
    }

    let stream = match sample_format {
        SampleFormat::F32 => build!(f32, |s: f32| s),
        SampleFormat::I16 => build!(i16, |s: i16| s as f32 / i16::MAX as f32),
        SampleFormat::U16 => build!(u16, |s: u16| (s as f32 / u16::MAX as f32) * 2.0 - 1.0),
        other => {
            *state.error.lock() = Some(format!("formato nao suportado: {other:?}"));
            state.stop.store(true, Ordering::Relaxed);
            return;
        }
    };

    let stream = match stream {
        Ok(s) => s,
        Err(e) => {
            *state.error.lock() = Some(e.to_string());
            state.stop.store(true, Ordering::Relaxed);
            return;
        }
    };

    if let Err(e) = stream.play() {
        *state.error.lock() = Some(e.to_string());
        state.stop.store(true, Ordering::Relaxed);
        return;
    }

    // Mantem o stream vivo nesta thread ate a parada.
    while !state.stop.load(Ordering::Relaxed) {
        std::thread::sleep(Duration::from_millis(100));
    }
    drop(stream);
}

pub(super) fn writer_loop(
    rx: Receiver<Vec<f32>>,
    paths: RecordingPaths,
    cfg: ResolvedConfig,
    state: Arc<RecorderState>,
) -> AppResult<()> {
    let bits = 16u16;
    let mut original = WavWriter::create(&paths.original, cfg.channels, cfg.sample_rate, bits)?;
    let mut processing = WavWriter::create(&paths.processing, 1, TARGET_SAMPLE_RATE, bits)?;
    let mut down = Downsampler::new(cfg.sample_rate, cfg.channels, TARGET_SAMPLE_RATE);

    let mut last_flush = Instant::now();
    let started = Instant::now();

    loop {
        match rx.recv_timeout(Duration::from_millis(200)) {
            Ok(block) => {
                let (mut sum_sq, mut peak) = (0.0f32, 0.0f32);
                for &s in &block {
                    sum_sq += s * s;
                    peak = peak.max(s.abs());
                }
                let rms = (sum_sq / block.len().max(1) as f32).sqrt();
                state
                    .level_milli
                    .store((rms.min(1.0) * 1000.0) as u32, Ordering::Relaxed);
                state
                    .peak_milli
                    .store((peak.min(1.0) * 1000.0) as u32, Ordering::Relaxed);

                let orig_i16: Vec<i16> = block
                    .iter()
                    .map(|s| (s.clamp(-1.0, 1.0) * i16::MAX as f32) as i16)
                    .collect();
                original.write_i16(&orig_i16)?;

                let mono = down.process(&block);
                if !mono.is_empty() {
                    processing.write_i16(&mono)?;
                }

                state
                    .duration_ms
                    .store(started.elapsed().as_millis() as u32, Ordering::Relaxed);
            }
            Err(RecvTimeoutError::Timeout) => {
                if state.stop.load(Ordering::Relaxed) {
                    break;
                }
            }
            Err(RecvTimeoutError::Disconnected) => break,
        }

        if last_flush.elapsed() >= Duration::from_secs(3) {
            original.flush()?;
            processing.flush()?;
            last_flush = Instant::now();
        }
        if state.stop.load(Ordering::Relaxed) {
            // Drena o que restou no canal antes de fechar.
            while let Ok(block) = rx.try_recv() {
                let orig_i16: Vec<i16> = block
                    .iter()
                    .map(|s| (s.clamp(-1.0, 1.0) * i16::MAX as f32) as i16)
                    .collect();
                original.write_i16(&orig_i16)?;
                let mono = down.process(&block);
                if !mono.is_empty() {
                    processing.write_i16(&mono)?;
                }
            }
            break;
        }
    }

    original.finalize()?;
    processing.finalize()?;
    Ok(())
}
