//! Fase 2: transcricao real com whisper.cpp.
//! Requer `ggml-large-v3-turbo-q5_0.bin` na pasta de modelos do app e o
//! fixture `2-two-speakers-en.wav`. Sem eles, o teste e ignorado.

use std::path::PathBuf;

use atalocal_lib::testing::transcribe as tt;

fn models_dir() -> Option<PathBuf> {
    let d = PathBuf::from(std::env::var_os("APPDATA")?)
        .join("local/AtaLocal/data/models");
    d.is_dir().then_some(d)
}

#[test]
fn transcreve_audio_curto() {
    let Some(models) = models_dir() else {
        eprintln!("modelos ausentes; ignorado");
        return;
    };
    // Usa o modelo mais leve disponivel — no Inspiron a RAM e o gargalo.
    let model = ["ggml-small-q5_1.bin", "ggml-large-v3-turbo-q5_0.bin"]
        .iter()
        .map(|n| models.join(n))
        .find(|p| p.exists())
        .unwrap_or_else(|| models.join("ggml-small-q5_1.bin"));
    let fixture = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests/fixtures/2-two-speakers-en.wav");
    if !model.exists() || !fixture.exists() {
        eprintln!("modelo/fixture ausente; ignorado");
        return;
    }

    let segs = tt::run(&model, &fixture).expect("transcricao falhou");
    assert!(!segs.is_empty(), "nenhum segmento");
    let texto: String = segs.iter().map(|(_, _, t)| t.as_str()).collect::<Vec<_>>().join(" ");
    assert!(texto.len() > 10, "texto muito curto: {texto:?}");
    eprintln!("transcrito: {texto}");
}
