# ADR 0004 — Diarização (separação de vozes)

## Decisão

`sherpa-rs` 0.6 (bindings do sherpa-onnx), diarização offline:
**segmentação pyannote-3.0** + **embedding de locutor campplus** (3D-Speaker,
zh+en, treino avançado), clustering rápido com **número de clusters fixado no
número de participantes cadastrados** (`AppSettings::participant_count`, padrão 3).

## Por quê

- O plano fixa "número conhecido de participantes = 3" → `num_clusters` em vez
  de limiar evita que ruído vire uma 4ª voz.
- `campplus_..._advanced` é multilíngue (pt não tem modelo dedicado no catálogo
  do Sherpa; o zh+en advanced generaliza melhor que os só-zh).
- `min_duration_on 0.3` / `min_duration_off 0.5`: numa sala presencial, exigir
  um mínimo de fala contínua reduz a troca-troca de locutor em silêncios curtos.

## Fluxo

1. Carrega o mesmo áudio 16 kHz mono da transcrição.
2. `Diarize::compute` → lista de `(start, end, speaker)` (spans de voz).
3. `assign_clusters`: para cada `transcript_segment`, escolhe o cluster com
   maior sobreposição temporal; < 0.2 s de sobreposição → `None`
   ("Não identificado", sem adivinhação — como pede o plano).
4. `segments.set_clusters` grava o cluster por segmento.

A **identificação nominal** (cluster → pessoa cadastrada) é etapa separada
(`identifying`), depende dos perfis de voz da Fase 3, e usa atribuição global
para não repetir nome.

## Robustez

Falha na diarização **não invalida** a transcrição: o pipeline registra o
motivo, segue para `identifying` e a reunião conclui sem separação de vozes.
Reprocessar (`process_meeting`) refaz a diarização de forma idempotente.

## Empacotamento

O modelo de segmentação vem em `.tar.bz2`. O downloader extrai (achatando a
pasta-raiz) para `models/<id>/`; `ModelManager::resolve_file` acha o `.onnx`.
