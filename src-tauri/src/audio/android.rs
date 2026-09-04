//! Captura Android usando a API nativa `android.media.AudioRecord`.
//!
//! O CPAL usa Oboe no Android. Esse backend funciona em muitos aparelhos, mas
//! falhas de permissao/rota podem sair do processo antes de virar um Result.
//! Aqui o Android abre e le o microfone diretamente, mantendo o restante do
//! pipeline (WAV, downsampling e FLAC) compartilhado com o desktop.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc;
use std::sync::Arc;
use std::time::Duration;

use jni::objects::{JObject, JValue};
use jni::{Executor, JNIEnv, JavaVM};
use parking_lot::Mutex;

use super::recorder::{self, RecorderHandle, RecorderState, RecordingPaths, RecordingSession};
use crate::error::{AppError, AppResult};

#[derive(Debug)]
struct AndroidJniError(String);

impl std::fmt::Display for AndroidJniError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

impl From<jni::errors::Error> for AndroidJniError {
    fn from(error: jni::errors::Error) -> Self {
        Self(format!("JNI Android: {error}"))
    }
}

const AUDIO_SOURCE_MIC: i32 = 1;
const CHANNEL_IN_MONO: i32 = 16;
const ENCODING_PCM_16BIT: i32 = 2;
const READ_NON_BLOCKING: i32 = 1;
const STATE_INITIALIZED: i32 = 1;

pub(super) fn start(paths: RecordingPaths) -> AppResult<RecordingSession> {
    let state = Arc::new(RecorderState {
        level_milli: std::sync::atomic::AtomicU32::new(0),
        peak_milli: std::sync::atomic::AtomicU32::new(0),
        duration_ms: std::sync::atomic::AtomicU32::new(0),
        stop: AtomicBool::new(false),
        error: Mutex::new(None),
    });
    let handle = RecorderHandle {
        inner: state.clone(),
    };
    let (tx, rx) = mpsc::channel::<Vec<f32>>();
    let (cfg_tx, cfg_rx) = mpsc::channel::<Result<recorder::ResolvedConfig, String>>();
    let capture_state = state.clone();

    let capture = std::thread::Builder::new()
        .name("atalocal-android-audio".into())
        .spawn(move || capture_loop(tx, cfg_tx, capture_state))
        .map_err(|e| AppError::Audio(e.to_string()))?;

    let cfg = match cfg_rx.recv_timeout(Duration::from_secs(5)) {
        Ok(Ok(cfg)) => cfg,
        Ok(Err(error)) => {
            state.stop.store(true, Ordering::Relaxed);
            let _ = capture.join();
            return Err(AppError::Audio(error));
        }
        Err(_) => {
            state.stop.store(true, Ordering::Relaxed);
            let _ = capture.join();
            return Err(AppError::Audio(
                "tempo esgotado ao abrir o microfone Android".into(),
            ));
        }
    };

    let writer_state = state.clone();
    let writer = std::thread::Builder::new()
        .name("atalocal-audio-writer".into())
        .spawn(move || recorder::writer_loop(rx, paths, cfg, writer_state))
        .map_err(|e| AppError::Audio(e.to_string()))?;

    Ok(RecordingSession {
        handle,
        capture: Some(capture),
        writer: Some(writer),
    })
}

fn with_attached<T>(
    f: impl FnOnce(&mut JNIEnv) -> Result<T, AndroidJniError>,
) -> Result<T, String> {
    let context = ndk_context::android_context();
    let vm = unsafe { JavaVM::from_raw(context.vm().cast()) }
        .map_err(|error| format!("JVM Android indisponivel: {error}"))?;
    Executor::new(Arc::new(vm))
        .with_attached(f)
        .map_err(|error| error.to_string())
}

fn permission_granted(env: &mut JNIEnv) -> Result<bool, AndroidJniError> {
    let context = unsafe { JObject::from_raw(ndk_context::android_context().context().cast()) };
    let permission = env.new_string("android.permission.RECORD_AUDIO")?;
    let result = env
        .call_method(
            &context,
            "checkSelfPermission",
            "(Ljava/lang/String;)I",
            &[JValue::from(&permission)],
        )?
        .i()?;
    Ok(result == 0)
}

fn min_buffer_size(env: &mut JNIEnv, rate: i32) -> Result<i32, AndroidJniError> {
    Ok(env
        .call_static_method(
            "android/media/AudioRecord",
            "getMinBufferSize",
            "(III)I",
            &[
                JValue::Int(rate),
                JValue::Int(CHANNEL_IN_MONO),
                JValue::Int(ENCODING_PCM_16BIT),
            ],
        )?
        .i()?)
}

fn capture_loop(
    tx: mpsc::Sender<Vec<f32>>,
    cfg_tx: mpsc::Sender<Result<recorder::ResolvedConfig, String>>,
    state: Arc<RecorderState>,
) {
    let mut config_sent = false;
    let result = with_attached(|env| {
        if !permission_granted(env)? {
            return Err(AndroidJniError("permissao RECORD_AUDIO negada".into()));
        }

        let mut selected = None;
        for rate in [16_000, 48_000, 44_100, 8_000] {
            let min = min_buffer_size(env, rate)?;
            if min > 0 {
                selected = Some((rate, min));
                break;
            }
        }
        let (sample_rate, min_bytes) = selected.ok_or_else(|| {
            AndroidJniError("o Android nao aceitou uma configuracao de audio".into())
        })?;
        let buffer_bytes = (min_bytes as usize).max(4096) & !1;
        let record = env.new_object(
            "android/media/AudioRecord",
            "(IIIII)V",
            &[
                JValue::Int(AUDIO_SOURCE_MIC),
                JValue::Int(sample_rate),
                JValue::Int(CHANNEL_IN_MONO),
                JValue::Int(ENCODING_PCM_16BIT),
                JValue::Int(buffer_bytes as i32),
            ],
        )?;
        let status = env.call_method(&record, "getState", "()I", &[])?.i()?;
        if status != STATE_INITIALIZED {
            return Err(AndroidJniError(
                "AudioRecord nao foi inicializado; verifique a permissao do microfone".into(),
            ));
        }
        env.call_method(&record, "startRecording", "()V", &[])?;

        let _ = cfg_tx.send(Ok(recorder::ResolvedConfig {
            sample_rate: sample_rate as u32,
            channels: 1,
        }));
        config_sent = true;

        let buffer = env.new_byte_array(buffer_bytes as i32)?;
        let mut raw = vec![0i8; buffer_bytes];
        while !state.stop.load(Ordering::Relaxed) {
            let read = env
                .call_method(
                    &record,
                    "read",
                    "([BIII)I",
                    &[
                        JValue::from(&buffer),
                        JValue::Int(0),
                        JValue::Int(buffer_bytes as i32),
                        JValue::Int(READ_NON_BLOCKING),
                    ],
                )?
                .i()?;
            if read < 0 {
                return Err(AndroidJniError(format!(
                    "AudioRecord.read falhou: codigo {read}"
                )));
            }
            if read >= 2 {
                env.get_byte_array_region(&buffer, 0, &mut raw[..read as usize])?;
                let samples: Vec<f32> = raw[..read as usize]
                    .chunks_exact(2)
                    .map(|pair| {
                        i16::from_le_bytes([pair[0] as u8, pair[1] as u8]) as f32 / i16::MAX as f32
                    })
                    .collect();
                if !samples.is_empty() {
                    let _ = tx.send(samples);
                }
            } else {
                std::thread::sleep(Duration::from_millis(10));
            }
        }
        let _ = env.call_method(&record, "stop", "()V", &[]);
        Ok(())
    });

    if let Err(error) = result {
        let message = error.to_string();
        if !config_sent {
            let _ = cfg_tx.send(Err(message.clone()));
        }
        *state.error.lock() = Some(message);
        state.stop.store(true, Ordering::Relaxed);
    }
}
