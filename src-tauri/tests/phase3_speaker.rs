//! Fase 3: extracao real de impressao de voz via C API do Sherpa-ONNX.
//! Requer os modelos locais e o fixture de audio; sem eles, o teste e ignorado.

use std::path::PathBuf;

use atalocal_lib::testing::speaker as ts;

fn models() -> Option<PathBuf> {
    let d = PathBuf::from(std::env::var_os("APPDATA")?)
        .join("local/AtaLocal/data/models");
    d.is_dir().then_some(d)
}

fn executable() -> Option<PathBuf> {
    let target = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("target/debug");
    ["atalocal.exe", "atalocal"]
        .into_iter()
        .map(|name| target.join(name))
        .find(|path| path.exists())
}

#[test]
fn extrai_embedding_de_voz() {
    let Some(m) = models() else {
        eprintln!("modelos ausentes; ignorado");
        return;
    };
    let Some(executable) = executable() else {
        eprintln!("atalocal auxiliar ausente; execute cargo build --bin atalocal; ignorado");
        return;
    };
    let model = m.join("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx");
    let library = m.join("sherpa-onnx-bin/lib/sherpa-onnx-c-api.dll");
    let wav = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests/fixtures/2-two-speakers-en.wav");
    if ![&model, &library, &wav].iter().all(|p| p.exists()) {
        eprintln!("modelo/DLL/fixture ausente; ignorado");
        return;
    }

    let dimension = ts::run(&executable, &model, &library, &wav)
        .expect("extracao de voz falhou");
    assert!(dimension > 0);
}
