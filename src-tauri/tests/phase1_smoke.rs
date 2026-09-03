//! Smoke test da Fase 1: diagnostico, schema do banco e catalogo de modelos.
//! Nao exige a janela Tauri — exercita a logica pura do backend.

use atalocal_lib::testing;

#[test]
fn diagnostico_reporta_hardware_e_microfones() {
    let tmp = std::env::temp_dir().join(format!("atalocal-test-{}", std::process::id()));
    std::fs::create_dir_all(&tmp).unwrap();

    let diag = testing::run_diagnostics_at(&tmp).expect("diagnostico falhou");

    assert!(diag.cpu_cores_logical >= 1);
    assert!(diag.total_ram_gb > 0.0);
    assert!(!diag.os_version.trim().is_empty());
    // Deve haver ao menos as 4 verificacoes (cpu, ram, disk, microphone).
    assert!(diag.checks.len() >= 4);
    assert!(diag.checks.iter().any(|c| c.id == "microphone"));

    let _ = std::fs::remove_dir_all(&tmp);
}

#[test]
fn schema_cria_todas_as_tabelas() {
    let tmp = std::env::temp_dir().join(format!("atalocal-db-{}", std::process::id()));
    std::fs::create_dir_all(&tmp).unwrap();

    let tables = testing::open_db_and_list_tables(&tmp).expect("abrir db falhou");
    for expected in [
        "speaker_profile",
        "meeting",
        "transcript_segment",
        "meeting_summary",
        "action_item",
        "processing_job",
        "app_settings",
        "model_state",
    ] {
        assert!(tables.contains(&expected.to_string()), "faltou tabela {expected}");
    }

    let _ = std::fs::remove_dir_all(&tmp);
}

#[test]
fn catalogo_de_modelos_e_consistente() {
    let catalog = testing::model_catalog();
    assert!(catalog.iter().any(|(id, ..)| *id == "whisper-large-v3-turbo-q5_0"));
    assert!(catalog.iter().any(|(id, ..)| *id == "qwen3-4b-instruct-q4_k_m"));
    // ids unicos, urls https, tamanho declarado > 0.
    let mut ids = std::collections::HashSet::new();
    for (id, url, size) in catalog {
        assert!(ids.insert(id), "id duplicado: {id}");
        assert!(url.starts_with("https://"), "url insegura: {url}");
        assert!(size > 0, "tamanho zero: {id}");
    }
}
