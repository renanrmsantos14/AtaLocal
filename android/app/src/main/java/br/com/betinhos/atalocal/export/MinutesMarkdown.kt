package br.com.betinhos.atalocal.export

import br.com.betinhos.atalocal.summarization.Minutes

fun minutesToMarkdown(minutes: Minutes): String = buildString {
    appendLine("# Ata da reunião")
    appendLine()
    appendLine("## Resumo")
    appendLine(minutes.summary)
    appendLine()
    section("Assuntos tratados", minutes.topics)
    section("Decisões", minutes.decisions)
    appendLine("## Tarefas")
    if (minutes.tasks.isEmpty()) appendLine("- Nenhuma tarefa identificada")
    minutes.tasks.forEach { task ->
        appendLine("- ${task.description}")
        appendLine("  - Responsável: ${task.assignee ?: "não informado"}")
        appendLine("  - Prazo: ${task.due ?: "não informado"}")
        appendLine("  - Evidência: ${task.evidence}")
    }
    appendLine()
    section("Pendências", minutes.pending)
    section("Alertas", minutes.alerts)
}

private fun StringBuilder.section(title: String, values: List<String>) {
    appendLine("## $title")
    if (values.isEmpty()) appendLine("- Nenhum item identificado")
    values.forEach { appendLine("- $it") }
    appendLine()
}
