//! Fase 2: diarizacao real com Sherpa-ONNX.
//!
//! Requer os modelos baixados na pasta local do app E um WAV de teste em
//! `tests/fixtures/2-two-speakers-en.wav` (nao versionado — baixe de
//! github.com/k2-fsa/sherpa-onnx releases/speaker-segmentation-models).
//! Sem esses arquivos, o teste e ignorado.

use std::path::{Path, PathBuf};

use atalocal_lib::testing::diarize as td;

fn app_models_dir() -> Option<PathBuf> {
    let base = dirs_data()?.join("local").join("AtaLocal").join("data").join("models");
    base.is_dir().then_some(base)
}

fn dirs_data() -> Option<PathBuf> {
    std::env::var_os("APPDATA").map(PathBuf::from)
}

#[test]
fn diariza_dois_locutores() {
    let fixture = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/fixtures/2-two-speakers-en.wav");
    let Some(models) = app_models_dir() else {
        eprintln!("modelos do app ausentes; teste ignorado");
        return;
    };
    let seg = models.join("sherpa-segmentation-pyannote/model.onnx");
    let emb = models.join("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx");
    if !fixture.exists() || !seg.exists() || !emb.exists() {
        eprintln!("fixtures/modelos ausentes; teste ignorado");
        return;
    }

    let spans = td::run(&seg, &emb, &fixture, Some(2)).expect("diarizacao falhou");
    assert!(!spans.is_empty(), "sem spans de voz");
    let vozes: std::collections::HashSet<_> = spans.iter().map(|(_, _, c)| *c).collect();
    assert_eq!(vozes.len(), 2, "esperava 2 vozes, veio {}", vozes.len());

    // Os spans devem estar ordenados e dentro de ~30s (duracao do fixture).
    let last_end = spans.last().map(|(_, e, _)| *e).unwrap_or(0.0);
    assert!(last_end > 1.0 && last_end < 60.0, "duracao inesperada: {last_end}");
}
