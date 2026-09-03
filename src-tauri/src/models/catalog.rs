use serde::Serialize;

/// Categoria funcional de um modelo.
#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ModelKind {
    Whisper,
    Diarization,
    Embedding,
    Llm,
    /// Executavel auxiliar (nao e um modelo, mas baixa pelo mesmo caminho).
    Tool,
}

/// Definicao estatica de um modelo baixavel.
#[derive(Debug, Clone)]
pub struct ModelDef {
    pub id: &'static str,
    pub kind: ModelKind,
    pub filename: &'static str,
    pub url: &'static str,
    /// SHA-256 em hex minusculo. String vazia = ainda nao fixado (Fase 1).
    pub sha256: &'static str,
    pub size_bytes: u64,
}

/// Catalogo de modelos. Os checksums dos candidatos a benchmark ficam vazios
/// ate serem fixados na Fase 1 (ver docs/adr/0002-modelos.md); o backend
/// registra o SHA-256 observado no primeiro download e passa a exigi-lo.
pub const CATALOG: &[ModelDef] = &[
    // ---- Transcricao (whisper.cpp / GGML) ----
    ModelDef {
        id: "whisper-small-q5_1",
        kind: ModelKind::Whisper,
        filename: "ggml-small-q5_1.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sha256: "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        size_bytes: 190_085_487,
    },
    ModelDef {
        id: "whisper-medium-q5_0",
        kind: ModelKind::Whisper,
        filename: "ggml-medium-q5_0.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        sha256: "",
        size_bytes: 539_212_467,
    },
    ModelDef {
        id: "whisper-large-v3-turbo-q5_0",
        kind: ModelKind::Whisper,
        filename: "ggml-large-v3-turbo-q5_0.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        sha256: "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        size_bytes: 574_041_195,
    },
    // ---- Diarizacao (Sherpa-ONNX) ----
    // O tag de release do upstream tem um typo: "speaker-recongition-models".
    ModelDef {
        id: "sherpa-segmentation-pyannote",
        kind: ModelKind::Diarization,
        filename: "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
        url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
        sha256: "24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488",
        size_bytes: 6_958_444,
    },
    ModelDef {
        id: "sherpa-speaker-embedding-campplus",
        kind: ModelKind::Embedding,
        filename: "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        sha256: "aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2",
        size_bytes: 28_281_164,
    },
    // Executavel de diarizacao (roda como subprocesso — ver ADR 0005).
    // Build "shared-MD-Release-no-tts": traz o .exe + onnxruntime.dll.
    ModelDef {
        id: "sherpa-onnx-bin",
        kind: ModelKind::Tool,
        filename: "sherpa-onnx-v1.13.7-win-x64.tar.bz2",
        url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-v1.13.7-win-x64-shared-MD-Release-no-tts.tar.bz2",
        sha256: "269d078c31cb176cb7c2c87952e9a8b30b19541df95445aaaa961c91a0760159",
        size_bytes: 18_752_734,
    },
    // ---- Resumo (llama.cpp — roda como subprocesso, ADR 0005) ----
    ModelDef {
        id: "llama-cpp-bin",
        kind: ModelKind::Tool,
        filename: "llama-b10793-bin-win-cpu-x64.zip",
        url: "https://github.com/ggml-org/llama.cpp/releases/download/b10793/llama-b10793-bin-win-cpu-x64.zip",
        sha256: "da6c5650bb1c97a81bc0c1594137d614bd566b8a54161898325e22f925271d7b",
        size_bytes: 18_389_766,
    },
    ModelDef {
        id: "qwen3-4b-instruct-q4_k_m",
        kind: ModelKind::Llm,
        filename: "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        url: "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sha256: "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
        size_bytes: 2_497_281_120,
    },
];

pub fn find(id: &str) -> Option<&'static ModelDef> {
    CATALOG.iter().find(|m| m.id == id)
}
