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
        sha256: "",
        size_bytes: 190_000_000,
    },
    ModelDef {
        id: "whisper-medium-q5_0",
        kind: ModelKind::Whisper,
        filename: "ggml-medium-q5_0.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        sha256: "",
        size_bytes: 539_000_000,
    },
    ModelDef {
        id: "whisper-large-v3-turbo-q5_0",
        kind: ModelKind::Whisper,
        filename: "ggml-large-v3-turbo-q5_0.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        sha256: "",
        size_bytes: 574_000_000,
    },
    // ---- Diarizacao (Sherpa-ONNX) ----
    ModelDef {
        id: "sherpa-segmentation-pyannote",
        kind: ModelKind::Diarization,
        filename: "sherpa-onnx-pyannote-segmentation-3-0.onnx",
        url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
        sha256: "",
        size_bytes: 6_000_000,
    },
    ModelDef {
        id: "sherpa-speaker-embedding-3dspeaker",
        kind: ModelKind::Embedding,
        filename: "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
        url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recognition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
        sha256: "",
        size_bytes: 38_000_000,
    },
    // ---- Resumo (llama.cpp) ----
    ModelDef {
        id: "qwen3-4b-instruct-q4_k_m",
        kind: ModelKind::Llm,
        filename: "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        url: "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sha256: "",
        size_bytes: 2_500_000_000,
    },
];

pub fn find(id: &str) -> Option<&'static ModelDef> {
    CATALOG.iter().find(|m| m.id == id)
}
