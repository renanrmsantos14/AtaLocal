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
    /// Preenchido apenas para modelos escolhiveis pelo usuario (whisper).
    pub profile: Option<ModelProfile>,
}

/// Caracteristicas de um modelo, mostradas na UI de selecao.
#[derive(Debug, Clone, Copy, Serialize)]
pub struct ModelProfile {
    /// Nome curto para exibir ("Rapido", "Equilibrado", "Maxima qualidade").
    pub label: &'static str,
    /// RAM aproximada durante a inferencia, em MB.
    pub ram_mb: u32,
    /// Velocidade relativa: quanto tempo de processamento por minuto de audio,
    /// em segundos, num CPU tipico de notebook (estimativa, ajustar com bench).
    pub secs_per_audio_min: u32,
    /// Qualidade da transcricao, 1..5.
    pub quality: u8,
    /// Descricao para o usuario.
    pub note: &'static str,
}

/// Catalogo de modelos. Os checksums dos candidatos a benchmark ficam vazios
/// ate serem fixados na Fase 1 (ver docs/adr/0002-modelos.md); o backend
/// registra o SHA-256 observado no primeiro download e passa a exigi-lo.
pub const CATALOG: &[ModelDef] = &[
    // ---- Transcricao (whisper.cpp / GGML) — escolhivel pelo usuario ----
    ModelDef {
        id: "whisper-small-q5_1",
        kind: ModelKind::Whisper,
        filename: "ggml-small-q5_1.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sha256: "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        size_bytes: 190_085_487,
        profile: Some(ModelProfile {
            label: "Rapido",
            ram_mb: 600,
            secs_per_audio_min: 20,
            quality: 3,
            note: "Leve e rapido. Bom para reunioes com audio limpo e poucas \
                pessoas. Erra mais em nomes proprios, sotaques fortes e fala \
                sobreposta. Recomendado se o computador tem pouca memoria.",
        }),
    },
    ModelDef {
        id: "whisper-medium-q5_0",
        kind: ModelKind::Whisper,
        filename: "ggml-medium-q5_0.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        sha256: "",
        size_bytes: 539_212_467,
        profile: Some(ModelProfile {
            label: "Equilibrado",
            ram_mb: 1700,
            secs_per_audio_min: 45,
            quality: 4,
            note: "Meio-termo entre velocidade e precisao. Lida melhor com \
                ruido e sotaques que o modelo Rapido. Precisa de folga de \
                memoria.",
        }),
    },
    ModelDef {
        id: "whisper-large-v3-turbo-q5_0",
        kind: ModelKind::Whisper,
        filename: "ggml-large-v3-turbo-q5_0.bin",
        url: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        sha256: "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        size_bytes: 574_041_195,
        profile: Some(ModelProfile {
            label: "Maxima qualidade",
            ram_mb: 2100,
            secs_per_audio_min: 55,
            quality: 5,
            note: "A transcricao mais precisa: melhor em nomes, numeros, \
                sotaques e fala rapida. E o mais pesado — em computadores com \
                menos de 8 GB de memoria livre pode falhar ao carregar.",
        }),
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
        profile: None,
    },
    ModelDef {
        id: "sherpa-speaker-embedding-campplus",
        kind: ModelKind::Embedding,
        filename: "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        sha256: "aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2",
        size_bytes: 28_281_164,
        profile: None,
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
        profile: None,
    },
    // ---- Resumo (llama.cpp — roda como subprocesso, ADR 0005) ----
    ModelDef {
        id: "llama-cpp-bin",
        kind: ModelKind::Tool,
        filename: "llama-b10793-bin-win-cpu-x64.zip",
        url: "https://github.com/ggml-org/llama.cpp/releases/download/b10793/llama-b10793-bin-win-cpu-x64.zip",
        sha256: "da6c5650bb1c97a81bc0c1594137d614bd566b8a54161898325e22f925271d7b",
        size_bytes: 18_389_766,
        profile: None,
    },
    ModelDef {
        id: "qwen3-4b-instruct-q4_k_m",
        kind: ModelKind::Llm,
        filename: "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        url: "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sha256: "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
        size_bytes: 2_497_281_120,
        profile: None,
    },
];

pub fn find(id: &str) -> Option<&'static ModelDef> {
    CATALOG.iter().find(|m| m.id == id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recomendacao_por_ram() {
        // 16 GB livres -> recomenda o de maior qualidade (large-turbo).
        let big = whisper_options(16_000);
        assert!(big.iter().find(|o| o.recommended).unwrap().id
            == "whisper-large-v3-turbo-q5_0");

        // 3 GB livres -> nada cabe com folga -> recomenda o mais leve.
        let small = whisper_options(3_000);
        assert_eq!(
            small.iter().find(|o| o.recommended).unwrap().id,
            "whisper-small-q5_1"
        );
        // e os pesados vem com aviso.
        assert!(small
            .iter()
            .find(|o| o.id == "whisper-large-v3-turbo-q5_0")
            .unwrap()
            .warning
            .is_some());
    }
}

/// Uma opcao de modelo de transcricao para a UI de selecao.
#[derive(Debug, Clone, Serialize)]
pub struct WhisperOption {
    pub id: &'static str,
    pub filename: &'static str,
    pub size_bytes: u64,
    pub profile: ModelProfile,
    /// true para o modelo que o app recomenda nesta maquina.
    pub recommended: bool,
    /// Aviso quando o modelo provavelmente nao roda bem aqui.
    pub warning: Option<String>,
}

/// Lista os modelos de transcricao com uma recomendacao baseada na RAM livre
/// (em MB). O recomendado e o mais capaz que cabe com folga.
pub fn whisper_options(available_ram_mb: u64) -> Vec<WhisperOption> {
    let whisper: Vec<&ModelDef> = CATALOG
        .iter()
        .filter(|m| m.kind == ModelKind::Whisper && m.profile.is_some())
        .collect();

    // Folga: precisa de ram_mb do modelo + ~1500 MB para o resto do sistema.
    let headroom = 1500u64;
    let fits = |p: &ModelProfile| available_ram_mb >= p.ram_mb as u64 + headroom;

    // Recomendado: o de maior qualidade que ainda "fits". Se nenhum couber,
    // recomenda o mais leve.
    let recommended_id = whisper
        .iter()
        .filter(|m| fits(&m.profile.unwrap()))
        .max_by_key(|m| m.profile.unwrap().quality)
        .or_else(|| whisper.iter().min_by_key(|m| m.profile.unwrap().ram_mb))
        .map(|m| m.id);

    whisper
        .iter()
        .map(|m| {
            let p = m.profile.unwrap();
            let warning = if !fits(&p) {
                Some(format!(
                    "Precisa de ~{} GB livres de memoria; este computador tem \
                     cerca de {:.1} GB. Pode ficar lento ou falhar.",
                    (p.ram_mb as u64 + headroom).div_ceil(1024),
                    available_ram_mb as f64 / 1024.0
                ))
            } else {
                None
            };
            WhisperOption {
                id: m.id,
                filename: m.filename,
                size_bytes: m.size_bytes,
                profile: p,
                recommended: recommended_id == Some(m.id),
                warning,
            }
        })
        .collect()
}
