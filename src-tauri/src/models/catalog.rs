use serde::Serialize;

/// Categoria funcional de um modelo.
#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ModelKind {
    Whisper,
    Diarization,
    Embedding,
    Llm,
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
    // ---- Resumo (llama.cpp) ----
    ModelDef {
        id: "qwen3-4b-instruct-q4_k_m",
        kind: ModelKind::Llm,
        filename: "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        url: "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sha256: "",
        size_bytes: 2_497_281_120,
    },
];

pub fn find(id: &str) -> Option<&'static ModelDef> {
    CATALOG.iter().find(|m| m.id == id)
}
