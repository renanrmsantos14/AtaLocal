//! Diarizacao: separa as falas por voz com Sherpa-ONNX (segmentacao pyannote +
//! embedding de locutor), agrupa em N clusters (N = participantes conhecidos) e
//! atribui cada segmento de transcricao ao cluster que mais se sobrepoe no tempo.

use std::path::Path;

use sherpa_rs::diarize::{Diarize, DiarizeConfig};

use crate::error::{AppError, AppResult};

/// Intervalo de fala atribuido a um cluster de voz.
#[derive(Debug, Clone)]
pub struct VoiceSpan {
    pub start_secs: f64,
    pub end_secs: f64,
    pub cluster: i64,
}

pub struct Diarizer {
    inner: Diarize,
}

impl Diarizer {
    /// `segmentation`: .onnx do pyannote (extraido do tar.bz2).
    /// `embedding`: .onnx do extrator de embedding de locutor.
    /// `num_speakers`: numero conhecido de participantes (>= 1). Se 0/None,
    ///   deixa o clustering decidir por limiar.
    pub fn load(
        segmentation: &Path,
        embedding: &Path,
        num_speakers: Option<i32>,
    ) -> AppResult<Self> {
        for (label, p) in [("segmentacao", segmentation), ("embedding", embedding)] {
            if !p.exists() {
                return Err(AppError::Model(format!(
                    "modelo de {label} ausente: {}",
                    p.display()
                )));
            }
        }

        let mut config = DiarizeConfig {
            // Sala presencial: exigir um minimo de fala continua reduz troca-troca.
            min_duration_on: Some(0.3),
            min_duration_off: Some(0.5),
            ..Default::default()
        };
        match num_speakers {
            Some(n) if n >= 1 => {
                config.num_clusters = Some(n);
                config.threshold = None;
            }
            _ => {
                config.num_clusters = None;
                config.threshold = Some(0.5);
            }
        }

        let inner = Diarize::new(segmentation, embedding, config)
            .map_err(|e| AppError::Other(format!("falha ao iniciar diarizacao: {e}")))?;
        Ok(Self { inner })
    }

    /// `samples`: mono f32, 16 kHz (mesmo audio da transcricao).
    pub fn run<F>(&mut self, samples: Vec<f32>, on_progress: F) -> AppResult<Vec<VoiceSpan>>
    where
        F: Fn(f32) + Send + 'static,
    {
        let cb = Box::new(move |done: i32, total: i32| {
            if total > 0 {
                on_progress(done as f32 / total as f32);
            }
            0
        });

        let segments = self
            .inner
            .compute(samples, Some(cb))
            .map_err(|e| AppError::Other(format!("diarizacao falhou: {e}")))?;

        Ok(segments
            .into_iter()
            .map(|s| VoiceSpan {
                start_secs: s.start as f64,
                end_secs: s.end as f64,
                cluster: s.speaker as i64,
            })
            .collect())
    }
}

/// Para cada `(start, end)` de transcricao, devolve o cluster com maior
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
    fn atribui_por_maior_sobreposicao() {
        let voice = vec![
            VoiceSpan { start_secs: 0.0, end_secs: 5.0, cluster: 0 },
            VoiceSpan { start_secs: 4.5, end_secs: 10.0, cluster: 1 },
        ];
        // segmento 0..4 -> todo no cluster 0
        // segmento 4..9 -> 0.5s no cluster 0, 4.5s no cluster 1 -> cluster 1
        // segmento 20..21 -> nada
        let got = assign_clusters(&[(0.0, 4.0), (4.0, 9.0), (20.0, 21.0)], &voice, 0.2);
        assert_eq!(got, vec![Some(0), Some(1), None]);
    }
}
