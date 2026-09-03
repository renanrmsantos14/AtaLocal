//! Fase 2: escrita WAV incremental, downsampling e conversao FLAC.
//! Nao abre o microfone — testa o caminho de dados de audio.

use atalocal_lib::testing::audio;

#[test]
fn wav_incremental_e_valido_apos_cada_flush() {
    let dir = tmp_dir("wav");
    let path = dir.join("t.wav");

    let mut w = audio::wav_create(&path, 1, 16_000, 16).unwrap();
    // 0,5 s de tom a 16 kHz.
    let tone: Vec<i16> = (0..8_000)
        .map(|i| ((i as f32 * 0.1).sin() * 10_000.0) as i16)
        .collect();
    w.write_i16(&tone).unwrap();
    w.flush().unwrap();

    // Le o cabecalho: RIFF/WAVE, PCM, 16 kHz, e data com o tamanho certo.
    let bytes = std::fs::read(&path).unwrap();
    assert_eq!(&bytes[0..4], b"RIFF");
    assert_eq!(&bytes[8..12], b"WAVE");
    let sr = u32::from_le_bytes([bytes[24], bytes[25], bytes[26], bytes[27]]);
    assert_eq!(sr, 16_000);
    let data_len = u32::from_le_bytes([bytes[40], bytes[41], bytes[42], bytes[43]]);
    assert_eq!(data_len as usize, tone.len() * 2);

    w.write_i16(&tone).unwrap();
    w.finalize().unwrap();
    let bytes = std::fs::read(&path).unwrap();
    let data_len = u32::from_le_bytes([bytes[40], bytes[41], bytes[42], bytes[43]]);
    assert_eq!(data_len as usize, tone.len() * 2 * 2);

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn downsampler_48k_estereo_para_16k_mono() {
    let mut d = audio::downsampler(48_000, 2, 16_000);
    // 4800 frames estereo (0,1 s a 48 kHz) -> ~1600 amostras a 16 kHz.
    let interleaved: Vec<f32> = (0..4800)
        .flat_map(|i| {
            let v = (i as f32 * 0.05).sin() * 0.5;
            [v, v]
        })
        .collect();
    let out = d.process(&interleaved);
    let ratio = out.len() as f32 / 1600.0;
    assert!((0.8..1.2).contains(&ratio), "esperava ~1600, veio {}", out.len());
}

#[test]
fn wav_para_flac_reduz_e_preserva_amostras() {
    let dir = tmp_dir("flac");
    let wav = dir.join("a.wav");
    let flac = dir.join("a.flac");

    let mut w = audio::wav_create(&wav, 1, 16_000, 16).unwrap();
    // 2 s de ruido de baixa entropia (senoide) — FLAC deve comprimir bem.
    let samples: Vec<i16> = (0..32_000)
        .map(|i| ((i as f32 * 0.02).sin() * 8_000.0) as i16)
        .collect();
    w.write_i16(&samples).unwrap();
    w.finalize().unwrap();

    let wav_size = std::fs::metadata(&wav).unwrap().len();
    let flac_size = audio::wav_to_flac(&wav, &flac).unwrap();

    assert!(flac_size > 0);
    assert!(
        flac_size < wav_size,
        "flac ({flac_size}) deveria ser menor que wav ({wav_size})"
    );

    let _ = std::fs::remove_dir_all(&dir);
}

fn tmp_dir(tag: &str) -> std::path::PathBuf {
    let d = std::env::temp_dir().join(format!(
        "atalocal-p2-{tag}-{}-{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    std::fs::create_dir_all(&d).unwrap();
    d
}
