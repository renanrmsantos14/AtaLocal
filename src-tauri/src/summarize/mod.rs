//! Resumo / ata: executa o `llama-cli.exe` (llama.cpp) como subprocesso com o
//! modelo Qwen3-4B. llama.cpp embarca ggml e conflita com o whisper.cpp no
//! mesmo executavel — por isso, subprocesso (mesma razao do sherpa, ADR 0005).
//!
//! Resumo em etapas para nao estourar memoria:
//!   1. divide a transcricao em blocos;
//!   2. extrai fatos/decisoes/acoes de cada bloco;
//!   3. consolida numa ata final estruturada (JSON).

use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;

use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};

/// Segmento de transcricao com dono (usado para montar o texto de entrada).
pub struct LabeledLine {
    pub start_secs: f64,
    pub speaker: String,
    pub text: String,
}

const SUMMARY_SYSTEM: &str =
    "Voce redige atas internas da Auto Prime Locações em portugues do Brasil. \
    Entregue um registro claro, firme e verificavel da reuniao. Responda SOMENTE \
    com um objeto JSON valido, sem texto antes ou depois, usando as chaves: \
    executive_summary (string), topics (array de string), decisions (array de \
    {text, timestamp}), action_items (array de {description, assignee, due}), \
    pending (array de {text, timestamp}), divergences (array de {text, timestamp}), \
    next_steps (array de string). O campo executive_summary e apenas o resumo geral \
    da reuniao; nao use a expressao resumo executivo. Resuma o contexto e os pontos \
    confirmados em 2 a 5 frases, sem propaganda e sem linguagem generica. Registre \
    uma decisao somente quando houver confirmacao explicita. Registre uma tarefa \
    somente quando houver compromisso ou encaminhamento claro; responsavel e prazo \
    devem ser null quando nao forem ditos. Pendencias sao assuntos ainda sem definicao. \
    Divergencias sao discordancias relevantes que permaneceram abertas. Preserve nomes, \
    datas, horarios, valores e numeros como aparecem na transcricao. Nao invente fatos, \
    responsaveis, prazos, decisoes ou proximos passos. Use arrays vazios e null onde \
    nao houver informacao. Elimine repeticoes, corrija a organizacao e mantenha o tom \
    operacional e objetivo.";

/// Aceita `null` como o valor padrao do tipo (o LLM as vezes emite null onde
/// pedimos um array ou string).
fn null_to_default<'de, D, T>(d: D) -> Result<T, D::Error>
where
    D: serde::Deserializer<'de>,
    T: Deserialize<'de> + Default,
{
    Ok(Option::<T>::deserialize(d)?.unwrap_or_default())
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct SummaryEntry {
    #[serde(default, deserialize_with = "null_to_default")]
    pub text: String,
    /// "HH:MM:SS" no audio, quando identificavel.
    #[serde(default)]
    pub timestamp: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ActionItemDraft {
    #[serde(default, deserialize_with = "null_to_default")]
    pub description: String,
    /// `None` quando ausente.
    #[serde(default)]
    pub assignee: Option<String>,
    #[serde(default)]
    pub due: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct MeetingMinutes {
    #[serde(default, deserialize_with = "null_to_default")]
    pub executive_summary: String,
    #[serde(default, deserialize_with = "null_to_default")]
    pub topics: Vec<String>,
    #[serde(default, deserialize_with = "null_to_default")]
    pub decisions: Vec<SummaryEntry>,
    #[serde(default, deserialize_with = "null_to_default")]
    pub action_items: Vec<ActionItemDraft>,
    #[serde(default, deserialize_with = "null_to_default")]
    pub pending: Vec<SummaryEntry>,
    #[serde(default, deserialize_with = "null_to_default")]
    pub divergences: Vec<SummaryEntry>,
    #[serde(default, deserialize_with = "null_to_default")]
    pub next_steps: Vec<String>,
}

pub struct Summarizer {
    exe: PathBuf,
    model: PathBuf,
    /// threads para o llama.cpp.
    threads: usize,
}

impl Summarizer {
    pub fn new(exe: &Path, model: &Path) -> AppResult<Self> {
        for (label, p) in [("executavel llama-cli", exe), ("modelo de resumo", model)] {
            if !p.exists() {
                return Err(AppError::Model(format!("{label} ausente: {}", p.display())));
            }
        }
        let threads = std::thread::available_parallelism()
            .map(|n| n.get())
            .unwrap_or(4)
            .clamp(2, 8);
        Ok(Self {
            exe: exe.to_path_buf(),
            model: model.to_path_buf(),
            threads,
        })
    }

    /// Gera a ata a partir das falas rotuladas. `on_progress` recebe 0..1.
    pub fn run<F>(&self, lines: &[LabeledLine], on_progress: F) -> AppResult<MeetingMinutes>
    where
        F: Fn(f32),
    {
        if lines.is_empty() {
            return Err(AppError::Other("transcricao vazia".into()));
        }

        // 1. Monta o texto e divide em blocos de ~1800 palavras.
        let full = lines
            .iter()
            .map(|l| format!("[{}] {}: {}", fmt_ts(l.start_secs), l.speaker, l.text))
            .collect::<Vec<_>>()
            .join("\n");
        let blocks = split_words(&full, 1800);
        // Para reunioes curtas, uma inferencia evita gerar notas intermediarias
        // e reduz pela metade o tempo de espera do usuario.
        let raw = if blocks.len() == 1 {
            on_progress(0.05);
            let prompt = format!(
                "Transcricao da reuniao:\n\n{}\n\nGere a ata em JSON.",
                blocks[0]
            );
            self.infer(SUMMARY_SYSTEM, &prompt, 900)?
        } else {
            let total_steps = blocks.len() + 1;

            // Reunioes longas passam por extracao em blocos para caber no contexto.
            let mut notes = Vec::with_capacity(blocks.len());
            for (i, block) in blocks.iter().enumerate() {
                on_progress(i as f32 / total_steps as f32);
                let sys = "Voce extrai fatos objetivos de uma reuniao em portugues. \
                    Liste decisoes, tarefas, pendencias e divergencias. Nao invente \
                    responsavel, prazo ou decisao. Seja conciso.";
                let prompt =
                    format!("Trecho da reuniao:\n\n{block}\n\nExtraia os fatos deste trecho.");
                notes.push(self.infer(sys, &prompt, 450)?);
            }

            on_progress(blocks.len() as f32 / total_steps as f32);
            let joined = notes.join("\n\n---\n\n");
            let prompt = format!("Notas da reuniao:\n\n{joined}\n\nGere a ata em JSON.");
            self.infer(SUMMARY_SYSTEM, &prompt, 1000)?
        };

        on_progress(1.0);
        parse_minutes(&raw)
    }

    /// Uma inferencia com o llama-cli. `sys` e `prompt` vao por arquivo para
    /// evitar limite de linha de comando; a saida (so a resposta) volta em stdout.
    fn infer(&self, sys: &str, prompt: &str, max_tokens: u32) -> AppResult<String> {
        let dir = std::env::temp_dir();
        let sys_file = dir.join(format!("atalocal-sys-{}.txt", std::process::id()));
        let prompt_file = dir.join(format!("atalocal-prompt-{}.txt", std::process::id()));
        write_file(&sys_file, sys)?;
        write_file(&prompt_file, prompt)?;

        let mut cmd = Command::new(&self.exe);
        cmd.arg("-m")
            .arg(&self.model)
            .arg("-sysf")
            .arg(&sys_file)
            .arg("-f")
            .arg(&prompt_file)
            .arg("-n")
            .arg(max_tokens.to_string())
            .arg("-t")
            .arg(self.threads.to_string())
            .arg("-c")
            .arg("8192")
            .arg("--temp")
            .arg("0.3")
            .arg("--single-turn")
            .arg("--no-display-prompt")
            .arg("--no-warmup");
        if let Some(bin) = self.exe.parent() {
            prepend_path(&mut cmd, bin);
        }

        let output = cmd
            .output()
            .map_err(|e| AppError::Other(format!("falha ao executar llama-cli: {e}")))?;

        let _ = std::fs::remove_file(&sys_file);
        let _ = std::fs::remove_file(&prompt_file);

        if !output.status.success() {
            let err = String::from_utf8_lossy(&output.stderr);
            return Err(AppError::Other(format!(
                "resumo falhou ({}): {}",
                output.status,
                err.lines().last().unwrap_or("").trim()
            )));
        }
        Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
    }
}

fn fmt_ts(secs: f64) -> String {
    let s = secs.max(0.0) as u64;
    format!("{:02}:{:02}:{:02}", s / 3600, (s % 3600) / 60, s % 60)
}

fn split_words(text: &str, per_block: usize) -> Vec<String> {
    let words: Vec<&str> = text.split_whitespace().collect();
    if words.len() <= per_block {
        return vec![text.to_string()];
    }
    words
        .chunks(per_block)
        .map(|c| c.join(" "))
        .collect()
}

/// Extrai o primeiro objeto JSON balanceado do texto (o modelo as vezes
/// adiciona comentarios antes/depois apesar da instrucao).
fn parse_minutes(raw: &str) -> AppResult<MeetingMinutes> {
    let start = raw.find('{').ok_or_else(|| {
        AppError::Other(format!("resumo sem JSON: {}", truncate(raw, 200)))
    })?;
    let mut depth = 0i32;
    let mut end = None;
    for (i, ch) in raw[start..].char_indices() {
        match ch {
            '{' => depth += 1,
            '}' => {
                depth -= 1;
                if depth == 0 {
                    end = Some(start + i + 1);
                    break;
                }
            }
            _ => {}
        }
    }
    let json = &raw[start..end.ok_or_else(|| AppError::Other("JSON incompleto no resumo".into()))?];
    serde_json::from_str::<MeetingMinutes>(json)
        .map_err(|e| AppError::Other(format!("JSON invalido do resumo: {e}; {}", truncate(json, 300))))
}

fn truncate(s: &str, n: usize) -> String {
    s.chars().take(n).collect()
}

fn write_file(path: &Path, content: &str) -> AppResult<()> {
    let mut f = std::fs::File::create(path)?;
    f.write_all(content.as_bytes())?;
    Ok(())
}

#[cfg(windows)]
fn prepend_path(cmd: &mut Command, dir: &Path) {
    let existing = std::env::var_os("PATH").unwrap_or_default();
    let mut paths = vec![dir.to_path_buf()];
    paths.extend(std::env::split_paths(&existing));
    if let Ok(joined) = std::env::join_paths(paths) {
        cmd.env("PATH", joined);
    }
}

#[cfg(not(windows))]
fn prepend_path(_cmd: &mut Command, _dir: &Path) {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn extrai_json_com_ruido_em_volta() {
        let raw = r#"Aqui esta a ata:
        {"executive_summary": "Reuniao sobre o projeto X.", "topics": ["orcamento"],
         "decisions": [], "action_items": [], "pending": [], "divergences": [],
         "next_steps": ["marcar follow-up"]}
        Espero ter ajudado!"#;
        let m = parse_minutes(raw).unwrap();
        assert_eq!(m.executive_summary, "Reuniao sobre o projeto X.");
        assert_eq!(m.topics, vec!["orcamento"]);
        assert_eq!(m.next_steps, vec!["marcar follow-up"]);
    }

    #[test]
    fn divide_em_blocos_por_palavras() {
        let text = (0..5000).map(|i| i.to_string()).collect::<Vec<_>>().join(" ");
        let blocks = split_words(&text, 1800);
        assert_eq!(blocks.len(), 3);
    }
}
