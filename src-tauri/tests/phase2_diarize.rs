//! Fase 2: diarizacao real via subprocesso sherpa-onnx.
//!
//! Requer os modelos + o binario do sherpa na pasta local do app E o WAV de
//! teste em `tests/fixtures/2-two-speakers-en.wav` (nao versionado).
//! Sem esses arquivos, o teste e ignorado.

use std::path::PathBuf;

use atalocal_lib::testing::diarize as td;

fn models() -> Option<PathBuf> {
    let d = PathBuf::from(std::env::var_os("APPDATA")?)
        .join("local/AtaLocal/data/models");
    d.is_dir().then_some(d)
}

#[test]
fn diariza_dois_locutores() {
    let Some(m) = models() else {
        eprintln!("modelos ausentes; ignorado");
        return;
    };
    let exe = m.join("sherpa-onnx-bin/bin/sherpa-onnx-offline-speaker-diarization.exe");
    let seg = m.join("sherpa-segmentation-pyannote/model.onnx");
    let emb = m.join("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx");
    let wav = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests/fixtures/2-two-speakers-en.wav");
    if ![&exe, &seg, &emb, &wav].iter().all(|p| p.exists()) {
        eprintln!("exe/modelos/fixture ausentes; ignorado");
        return;
    }

    let spans = td::run(&exe, &seg, &emb, &wav, Some(2)).expect("diarizacao falhou");
    assert!(!spans.is_empty());
    let vozes: std::collections::HashSet<_> = spans.iter().map(|(_, _, c)| *c).collect();
    assert_eq!(vozes.len(), 2, "esperava 2 vozes, veio {}", vozes.len());
}
