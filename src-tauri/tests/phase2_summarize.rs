//! Fase 2: resumo real via subprocesso llama.cpp + Qwen3.
//! Requer o binario do llama e o modelo Qwen na pasta local do app.
//! Sem eles, o teste e ignorado.

use std::path::PathBuf;

use atalocal_lib::testing::summarize as ts;

fn models() -> Option<PathBuf> {
    let d = PathBuf::from(std::env::var_os("APPDATA")?)
        .join("local/AtaLocal/data/models");
    d.is_dir().then_some(d)
}

#[test]
fn gera_ata_de_transcricao_curta() {
    let Some(m) = models() else {
        eprintln!("modelos ausentes; ignorado");
        return;
    };
    let exe = m.join("llama-cpp-bin/llama-cli.exe");
    let model = m.join("Qwen3-4B-Instruct-2507-Q4_K_M.gguf");
    if !exe.exists() || !model.exists() {
        eprintln!("exe/modelo ausente; ignorado");
        return;
    }

    let lines = vec![
        (0.0, "Ana".to_string(), "Bom dia. Vamos decidir o orcamento do projeto novo.".to_string()),
        (6.0, "Bruno".to_string(), "Proponho quinze mil reais para a primeira fase.".to_string()),
        (12.0, "Ana".to_string(), "Aprovado. Bruno, monta a planilha ate sexta-feira.".to_string()),
        (18.0, "Bruno".to_string(), "Combinado. Fica pendente definir o fornecedor.".to_string()),
    ];

    let minutes = ts::run(&exe, &model, &lines).expect("resumo falhou");
    assert!(!minutes.executive_summary.is_empty(), "sem resumo executivo");
    eprintln!("resumo: {}", minutes.executive_summary);
    eprintln!("decisoes: {:?}", minutes.decisions);
    eprintln!("tarefas: {:?}", minutes.action_items);
    // Ao menos uma decisao OU uma tarefa deve ter sido extraida.
    assert!(
        !minutes.decisions.is_empty() || !minutes.action_items.is_empty(),
        "nenhuma decisao/tarefa extraida"
    );
}
