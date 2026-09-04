//! Diarizacao (separacao de vozes): executa o `sherpa-onnx-offline-speaker-
//! diarization.exe` como subprocesso. Bindings compilados do sherpa-onnx
//! conflitam com o whisper.cpp no mesmo executavel; um processo separado
//! isola as duas bibliotecas C++. Ver docs/adr/0005-diarizacao-subprocesso.md.
//!
//! Segmentacao pyannote-3.0 + embedding campplus, N clusters = participantes.
//! Cada segmento de transcricao recebe o cluster de maior sobreposicao temporal.

use std::path::{Path, PathBuf};
use std::process::Command;

use crate::error::{AppError, AppResult};

/// Intervalo de fala atribuido a um cluster de voz.
#[derive(Debug, Clone)]
pub struct VoiceSpan {
    pub start_secs: f64,
    pub end_secs: f64,
    pub cluster: i64,
}

/// Executa a diarizacao offline. `audio` deve ser WAV mono 16 kHz.
///
/// - `exe`: caminho do `sherpa-onnx-offline-speaker-diarization.exe`
/// - `segmentation`: `model.onnx` do pyannote
/// - `embedding`: `.onnx` do extrator de embedding
/// - `num_speakers`: participantes conhecidos (>=1) => `--clustering.num-clusters`;
///   None => limiar automatico.
pub fn run(
    exe: &Path,
    segmentation: &Path,
    embedding: &Path,
    audio: &Path,
    num_speakers: Option<i32>,
) -> AppResult<Vec<VoiceSpan>> {
    #[cfg(target_os = "android")]
    return Err(AppError::Model(
        "separacao de vozes ainda nao esta disponivel no Android".into(),
    ));

    for (label, p) in [
        ("executavel de diarizacao", exe),
        ("modelo de segmentacao", segmentation),
        ("modelo de embedding", embedding),
        ("audio", audio),
    ] {
        if !p.exists() {
            return Err(AppError::Model(format!("{label} ausente: {}", p.display())));
        }
    }

    let mut cmd = Command::new(exe);
    cmd.arg(format!(
        "--segmentation.pyannote-model={}",
        segmentation.display()
    ))
    .arg(format!("--embedding.model={}", embedding.display()))
    .arg("--segmentation.num-threads=2")
    .arg("--embedding.num-threads=2")
    .arg("--min-duration-on=0.3")
    .arg("--min-duration-off=0.5");

    match num_speakers {
        Some(n) if n >= 1 => {
            cmd.arg(format!("--clustering.num-clusters={n}"));
        }
        _ => {
            cmd.arg("--clustering.cluster-threshold=0.90");
        }
    }
    cmd.arg(audio);

    // O exe precisa das DLLs (onnxruntime, sherpa-onnx-c-api). No pacote do
    // sherpa elas ficam em `bin/` e `lib/`, irmaos do diretorio do exe.
    if let Some(bin) = exe.parent() {
        let mut extra = vec![bin.to_path_buf()];
        if let Some(root) = bin.parent() {
            extra.push(root.join("lib"));
        }
        prepend_path(&mut cmd, &extra);
    }

    let output = cmd
        .output()
        .map_err(|e| AppError::Other(format!("falha ao executar diarizacao: {e}")))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(AppError::Other(format!(
            "diarizacao retornou erro ({}): {}",
            output.status,
            stderr.lines().last().unwrap_or("").trim()
        )));
    }

    // Combina stdout+stderr: o exe imprime as linhas de resultado em stdout,
    // mas algumas builds usam stderr.
    let text = format!(
        "{}\n{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    let spans = parse_output(&text);
    if spans.is_empty() {
        return Err(AppError::Other(
            "diarizacao nao produziu segmentos".into(),
        ));
    }
    Ok(spans)
}

/// Extrai linhas `INICIO -- FIM speaker_NN`.
fn parse_output(text: &str) -> Vec<VoiceSpan> {
    let mut spans = Vec::new();
    for line in text.lines() {
        let line = line.trim();
        // formato: "0.031 -- 2.765 speaker_00"
        let Some((times, spk)) = line.rsplit_once(' ') else {
            continue;
        };
        if !spk.starts_with("speaker_") {
            continue;
        }
        let Some(cluster) = spk.trim_start_matches("speaker_").parse::<i64>().ok() else {
            continue;
        };
        let parts: Vec<&str> = times.split("--").map(str::trim).collect();
        if parts.len() != 2 {
            continue;
        }
        let (Ok(start), Ok(end)) = (parts[0].parse::<f64>(), parts[1].parse::<f64>()) else {
            continue;
        };
        spans.push(VoiceSpan {
            start_secs: start,
            end_secs: end,
            cluster,
        });
    }
    spans.sort_by(|a, b| a.start_secs.total_cmp(&b.start_secs));
    spans
}

#[cfg(windows)]
fn prepend_path(cmd: &mut Command, dirs: &[PathBuf]) {
    let existing = std::env::var_os("PATH").unwrap_or_default();
    let mut paths: Vec<PathBuf> = dirs.to_vec();
    paths.extend(std::env::split_paths(&existing));
    if let Ok(joined) = std::env::join_paths(paths) {
        cmd.env("PATH", joined);
    }
}

#[cfg(not(windows))]
fn prepend_path(_cmd: &mut Command, _dirs: &[PathBuf]) {}

/// Para cada `(start, end)` de transcricao, devolve o cluster de maior
/// sobreposicao temporal, ou `None` se nenhuma passa de `min_overlap` segundos.
pub fn assign_clusters(
    transcript: &[(f64, f64)],
    voice: &[VoiceSpan],
    min_overlap: f64,
) -> Vec<Option<i64>> {
    transcript
        .iter()
        .map(|&(ts, te)| {
            let mut best: Option<(i64, f64)> = None;
            for v in voice {
                let ov = (te.min(v.end_secs) - ts.max(v.start_secs)).max(0.0);
                if ov <= 0.0 {
                    continue;
                }
                match best {
                    Some((_, b)) if ov <= b => {}
                    _ => best = Some((v.cluster, ov)),
                }
            }
            match best {
                Some((c, ov)) if ov >= min_overlap => Some(c),
                _ => None,
            }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parseia_saida_do_exe() {
        let out = "\
progress 100.00%
Started
0.031 -- 2.765 speaker_00
2.765 -- 7.810 speaker_01
7.844 -- 11.118 speaker_00
";
        let spans = parse_output(out);
        assert_eq!(spans.len(), 3);
        assert_eq!(spans[0].cluster, 0);
        assert_eq!(spans[1].cluster, 1);
        assert!((spans[1].start_secs - 2.765).abs() < 1e-6);
    }

    #[test]
    fn atribui_por_maior_sobreposicao() {
        let voice = vec![
            VoiceSpan { start_secs: 0.0, end_secs: 5.0, cluster: 0 },
            VoiceSpan { start_secs: 4.5, end_secs: 10.0, cluster: 1 },
        ];
        let got = assign_clusters(&[(0.0, 4.0), (4.0, 9.0), (20.0, 21.0)], &voice, 0.2);
        assert_eq!(got, vec![Some(0), Some(1), None]);
    }
}
