//! Extracao local de impressao de voz usando a DLL C do Sherpa-ONNX.
//!
//! A diarizacao continua isolada em subprocesso. A DLL e carregada apenas para
//! cadastrar e comparar perfis, evitando linkar o Sherpa ao executavel Tauri.

use std::ffi::{c_char, c_void, CString};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

use libloading::{Library, Symbol};

use crate::error::{AppError, AppResult};
use crate::models::ModelManager;

const SAMPLE_RATE: i32 = 16_000;
const MAX_CLUSTER_SECONDS: usize = 60;

#[repr(C)]
struct ExtractorConfig {
    model: *const c_char,
    num_threads: i32,
    debug: i32,
    provider: *const c_char,
}

type ExtractorHandle = c_void;
type StreamHandle = c_void;
type CreateExtractor = unsafe extern "C" fn(*const ExtractorConfig) -> *const ExtractorHandle;
type DestroyExtractor = unsafe extern "C" fn(*const ExtractorHandle);
type ExtractorDim = unsafe extern "C" fn(*const ExtractorHandle) -> i32;
type CreateStream = unsafe extern "C" fn(*const ExtractorHandle) -> *const StreamHandle;
type DestroyStream = unsafe extern "C" fn(*const StreamHandle);
type AcceptWaveform = unsafe extern "C" fn(*const StreamHandle, i32, *const f32, i32);
type InputFinished = unsafe extern "C" fn(*const StreamHandle);
type IsReady = unsafe extern "C" fn(*const ExtractorHandle, *const StreamHandle) -> i32;
type ComputeEmbedding =
    unsafe extern "C" fn(*const ExtractorHandle, *const StreamHandle) -> *const f32;
type DestroyEmbedding = unsafe extern "C" fn(*const f32);

pub fn model_paths(models: &ModelManager) -> AppResult<(PathBuf, PathBuf)> {
    #[cfg(target_os = "android")]
    return Err(AppError::Model(
        "identificacao de vozes ainda nao esta disponivel no Android".into(),
    ));

    let embedding = models.resolve_file("sherpa-speaker-embedding-campplus", None)?;
    let library = models.resolve_file("sherpa-onnx-bin", Some("lib/sherpa-onnx-c-api.dll"))?;
    Ok((embedding, library))
}

pub fn samples_for_ranges(samples: &[f32], ranges: &[(f64, f64)]) -> Vec<f32> {
    let max_samples = MAX_CLUSTER_SECONDS * SAMPLE_RATE as usize;
    let mut selected = Vec::with_capacity(max_samples.min(samples.len()));
    for &(start, end) in ranges {
        if selected.len() >= max_samples || end <= start {
            break;
        }
        let from = (start.max(0.0) * SAMPLE_RATE as f64).floor() as usize;
        let to = (end.max(0.0) * SAMPLE_RATE as f64).ceil() as usize;
        if from >= samples.len() || to <= from {
            continue;
        }
        let to = to.min(samples.len());
        let remaining = max_samples - selected.len();
        selected.extend_from_slice(&samples[from..to.min(from + remaining)]);
    }
    selected
}

pub fn extract(library_path: &Path, model_path: &Path, samples: &[f32]) -> AppResult<Vec<f32>> {
    if samples.len() < SAMPLE_RATE as usize {
        return Err(AppError::Other(
            "a amostra precisa ter pelo menos 1 segundo de fala".into(),
        ));
    }
    let model = CString::new(model_path.to_string_lossy().as_bytes())
        .map_err(|_| AppError::Other("caminho do modelo de voz invalido".into()))?;
    let provider = CString::new("cpu").expect("cpu sem NUL");

    // Carrega primeiro o ONNX Runtime que acompanha o Sherpa. Em ambiente de
    // desenvolvimento pode existir outra `onnxruntime.dll` ao lado do exe
    // (por exemplo, uma trazida por outra dependencia Rust), e o carregador do
    // Windows prioriza essa pasta antes do diretorio atual.
    let runtime_library = library_path
        .parent()
        .map(|dir| dir.join("onnxruntime.dll"))
        .filter(|path| path.exists())
        .map(|path| unsafe { Library::new(path) })
        .transpose()
        .map_err(|e| AppError::Model(format!("nao foi possivel carregar o ONNX Runtime da voz: {e}")))?;

    // SAFETY: os nomes e assinaturas abaixo correspondem ao C API distribuido
    // junto da mesma versao do Sherpa-ONNX usada pelos modelos do app.
    let library = unsafe { Library::new(library_path) }
        .map_err(|e| AppError::Model(format!("nao foi possivel carregar a DLL de voz: {e}")))?;
    let config = ExtractorConfig {
        model: model.as_ptr(),
        num_threads: 2,
        debug: 0,
        provider: provider.as_ptr(),
    };

    unsafe {
        let create: Symbol<CreateExtractor> = library
            .get(b"SherpaOnnxCreateSpeakerEmbeddingExtractor\0")
            .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
        let destroy: Symbol<DestroyExtractor> = library
            .get(b"SherpaOnnxDestroySpeakerEmbeddingExtractor\0")
            .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
        let extractor = create(&config);
        if extractor.is_null() {
            return Err(AppError::Model(
                "modelo de impressão de voz invalido".into(),
            ));
        }

        let result = extract_with_handle(&library, extractor, samples);
        destroy(extractor);
        drop(runtime_library);
        result
    }
}

/// Executa a extracao fora do processo Tauri.
///
/// O whisper.cpp e o Sherpa-ONNX podem carregar versoes diferentes do
/// `onnxruntime.dll`. O processo auxiliar e o proprio executavel do app
/// iniciado com `--speaker-helper`, portanto nao compartilha DLLs ja
/// carregadas pela janela principal.
pub fn extract_isolated(
    executable: &Path,
    library_path: &Path,
    model_path: &Path,
    samples: &[f32],
) -> AppResult<Vec<f32>> {
    if !executable.exists() {
        return Err(AppError::Model(format!(
            "executavel auxiliar de voz ausente: {}",
            executable.display()
        )));
    }
    if !library_path.exists() || !model_path.exists() {
        return Err(AppError::Model("modelo de voz ou DLL ausente".into()));
    }
    if samples.len() < SAMPLE_RATE as usize {
        return Err(AppError::Other(
            "a amostra precisa ter pelo menos 1 segundo de fala".into(),
        ));
    }

    let mut command = Command::new(executable);
    command
        .arg("--speaker-helper")
        .arg(model_path)
        .arg(library_path)
        .current_dir(library_path.parent().unwrap_or_else(|| Path::new(".")))
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    prepend_path(
        &mut command,
        &[library_path
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .to_path_buf()],
    );

    let mut child = command
        .spawn()
        .map_err(|e| AppError::Other(format!("falha ao iniciar auxiliar de voz: {e}")))?;
    {
        let mut input = child
            .stdin
            .take()
            .ok_or_else(|| AppError::Other("auxiliar de voz nao aceitou a amostra".into()))?;
        let bytes = samples
            .iter()
            .flat_map(|sample| sample.to_le_bytes())
            .collect::<Vec<u8>>();
        input.write_all(&bytes)?;
    }

    let deadline = Instant::now() + Duration::from_secs(120);
    loop {
        match child.try_wait()? {
            Some(_) => break,
            None if Instant::now() >= deadline => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(AppError::Model(
                    "auxiliar de voz excedeu o limite de 120 segundos".into(),
                ));
            }
            None => std::thread::sleep(Duration::from_millis(100)),
        }
    }

    let output = child
        .wait_with_output()
        .map_err(|e| AppError::Other(format!("falha ao finalizar auxiliar de voz: {e}")))?;
    if !output.status.success() {
        let detail = String::from_utf8_lossy(&output.stderr);
        return Err(AppError::Model(format!(
            "auxiliar de voz retornou erro ({}): {}",
            output.status,
            detail.lines().last().unwrap_or("erro desconhecido").trim()
        )));
    }
    if output.stdout.len() % std::mem::size_of::<f32>() != 0 {
        return Err(AppError::Model(
            "auxiliar de voz retornou dados invalidos".into(),
        ));
    }
    let embedding = output
        .stdout
        .chunks_exact(std::mem::size_of::<f32>())
        .map(|chunk| f32::from_le_bytes(chunk.try_into().expect("bloco de f32")))
        .collect::<Vec<_>>();
    if embedding.is_empty() {
        return Err(AppError::Model(
            "auxiliar de voz nao retornou impressao".into(),
        ));
    }
    Ok(embedding)
}

/// Ponto de entrada do mesmo executavel quando iniciado como auxiliar.
pub fn run_helper(args: impl Iterator<Item = String>) -> i32 {
    let mut args = args.skip(1);
    let Some(flag) = args.next() else {
        return 0;
    };
    if flag != "--speaker-helper" {
        return 0;
    }
    let (Some(model), Some(library)) = (args.next(), args.next()) else {
        eprintln!("auxiliar de voz: argumentos incompletos");
        return 2;
    };

    let mut bytes = Vec::new();
    if let Err(error) = std::io::stdin().read_to_end(&mut bytes) {
        eprintln!("auxiliar de voz: falha ao ler audio: {error}");
        return 2;
    }
    if bytes.len() % std::mem::size_of::<f32>() != 0 {
        eprintln!("auxiliar de voz: audio em formato invalido");
        return 2;
    }
    let samples = bytes
        .chunks_exact(std::mem::size_of::<f32>())
        .map(|chunk| f32::from_le_bytes(chunk.try_into().expect("bloco de f32")))
        .collect::<Vec<_>>();

    match extract(Path::new(&library), Path::new(&model), &samples) {
        Ok(embedding) => {
            let mut stdout = std::io::stdout().lock();
            for sample in embedding {
                if let Err(error) = stdout.write_all(&sample.to_le_bytes()) {
                    eprintln!("auxiliar de voz: falha ao escrever resultado: {error}");
                    return 2;
                }
            }
            0
        }
        Err(error) => {
            eprintln!("{error}");
            1
        }
    }
}

pub fn helper_executable() -> AppResult<PathBuf> {
    std::env::current_exe()
        .map_err(|e| AppError::Other(format!("nao foi possivel localizar o app: {e}")))
}

unsafe fn extract_with_handle(
    library: &Library,
    extractor: *const ExtractorHandle,
    samples: &[f32],
) -> AppResult<Vec<f32>> {
    let create_stream: Symbol<CreateStream> = library
        .get(b"SherpaOnnxSpeakerEmbeddingExtractorCreateStream\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let destroy_stream: Symbol<DestroyStream> = library
        .get(b"SherpaOnnxDestroyOnlineStream\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let accept: Symbol<AcceptWaveform> = library
        .get(b"SherpaOnnxOnlineStreamAcceptWaveform\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let finished: Symbol<InputFinished> = library
        .get(b"SherpaOnnxOnlineStreamInputFinished\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let ready: Symbol<IsReady> = library
        .get(b"SherpaOnnxSpeakerEmbeddingExtractorIsReady\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let dim: Symbol<ExtractorDim> = library
        .get(b"SherpaOnnxSpeakerEmbeddingExtractorDim\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let compute: Symbol<ComputeEmbedding> = library
        .get(b"SherpaOnnxSpeakerEmbeddingExtractorComputeEmbedding\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;
    let destroy_embedding: Symbol<DestroyEmbedding> = library
        .get(b"SherpaOnnxSpeakerEmbeddingExtractorDestroyEmbedding\0")
        .map_err(|e| AppError::Model(format!("API de voz incompleta: {e}")))?;

    let stream = create_stream(extractor);
    if stream.is_null() {
        return Err(AppError::Other(
            "nao foi possivel criar fluxo de voz".into(),
        ));
    }
    accept(stream, SAMPLE_RATE, samples.as_ptr(), samples.len() as i32);
    finished(stream);
    if ready(extractor, stream) == 0 {
        destroy_stream(stream);
        return Err(AppError::Other(
            "nao houve fala suficiente para criar a impressao de voz".into(),
        ));
    }

    let pointer = compute(extractor, stream);
    if pointer.is_null() {
        destroy_stream(stream);
        return Err(AppError::Other(
            "o modelo nao gerou uma impressao de voz".into(),
        ));
    }
    let dimension = dim(extractor);
    if dimension <= 0 {
        destroy_embedding(pointer);
        destroy_stream(stream);
        return Err(AppError::Other("dimensao de voz invalida".into()));
    }
    let embedding = std::slice::from_raw_parts(pointer, dimension as usize).to_vec();
    destroy_embedding(pointer);
    destroy_stream(stream);
    Ok(embedding)
}

#[cfg(windows)]
fn prepend_path(command: &mut Command, dirs: &[PathBuf]) {
    let existing = std::env::var_os("PATH").unwrap_or_default();
    let mut paths: Vec<PathBuf> = dirs.to_vec();
    paths.extend(std::env::split_paths(&existing));
    if let Ok(joined) = std::env::join_paths(paths) {
        command.env("PATH", joined);
    }
}

pub fn cosine_similarity(left: &[f32], right: &[f32]) -> Option<f64> {
    if left.len() != right.len() || left.is_empty() {
        return None;
    }
    let (dot, left_norm, right_norm) =
        left.iter()
            .zip(right)
            .fold((0.0_f64, 0.0_f64, 0.0_f64), |(dot, ln, rn), (a, b)| {
                let (a, b) = (*a as f64, *b as f64);
                (dot + a * b, ln + a * a, rn + b * b)
            });
    let denominator = left_norm.sqrt() * right_norm.sqrt();
    (denominator > 0.0).then_some(dot / denominator)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn limita_amostras_por_cluster() {
        let samples = vec![1.0; SAMPLE_RATE as usize * 61];
        let selected = samples_for_ranges(&samples, &[(0.0, 40.0), (40.0, 61.0)]);
        assert_eq!(selected.len(), MAX_CLUSTER_SECONDS * SAMPLE_RATE as usize);
    }

    #[test]
    fn calcula_similaridade_coseno() {
        assert_eq!(cosine_similarity(&[1.0, 0.0], &[1.0, 0.0]), Some(1.0));
        assert_eq!(cosine_similarity(&[1.0, 0.0], &[0.0, 1.0]), Some(0.0));
    }
}
