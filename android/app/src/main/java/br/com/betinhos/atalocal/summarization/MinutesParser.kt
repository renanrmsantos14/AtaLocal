package br.com.betinhos.atalocal.summarization

import org.json.JSONArray
import org.json.JSONObject

data class MinuteTask(val description: String, val assignee: String?, val due: String?, val evidence: String)
data class Minutes(val summary: String, val topics: List<String>, val decisions: List<String>, val tasks: List<MinuteTask>, val pending: List<String>, val alerts: List<String>, val participants: List<String> = emptyList())

fun parseMinutesOrFallback(raw: String, transcript: String = ""): Minutes = runCatching { parseMinutes(raw) }
    .map { it.validateAgainst(transcript) }
    .getOrElse {
    Minutes(
        summary = "A ata automática não pôde ser estruturada.",
        participants = emptyList(),
        topics = emptyList(),
        decisions = emptyList(),
        tasks = emptyList(),
        pending = emptyList(),
        alerts = listOf("JSON inválido retornado pelo modelo. Revise a transcrição e tente regenerar a ata.")
    )
}

private fun Minutes.validateAgainst(transcript: String): Minutes {
    if (transcript.isBlank()) return this
    val normalizedTranscript = transcript.lowercase()
    val validParticipants = participants.filter { normalizedTranscript.contains(it.lowercase()) }
    val validTasks = tasks.filter { task ->
        val evidenceWords = task.evidence.lowercase().split(Regex("\\W+")).filter { it.length >= 4 }
        evidenceWords.any(normalizedTranscript::contains)
    }
    val discarded = (participants.size - validParticipants.size) + (tasks.size - validTasks.size)
    return copy(
        participants = validParticipants,
        tasks = validTasks,
        alerts = if (discarded == 0) alerts else alerts + "$discarded item(ns) sem evidência suficiente foram removidos da ata."
    )
}

fun parseMinutes(raw: String): Minutes {
    val json = JSONObject(extractJsonObject(raw))
    fun strings(key: String) = json.optJSONArray(key).toStrings()
    val participants = strings("participantes")
    val topics = strings("assuntos")
    val decisions = strings("decisoes")
    val tasks = json.optJSONArray("tarefas").toTasks()
    val pending = strings("pendencias")
    val alerts = strings("alertas")
    val summary = json.optString("resumo").trim().ifEmpty {
        if (participants.isEmpty() && topics.isEmpty() && decisions.isEmpty() && tasks.isEmpty() && pending.isEmpty()) {
            "A transcrição não contém conteúdo suficiente para resumir."
        } else {
            "O modelo não forneceu um resumo; revise os itens estruturados abaixo."
        }
    }
    return Minutes(
        summary = summary,
        participants = participants,
        topics = topics,
        decisions = decisions,
        tasks = tasks,
        pending = pending,
        alerts = alerts
    )
}

private fun extractJsonObject(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) return trimmed
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    require(start >= 0 && end > start) { "Objeto JSON ausente" }
    return trimmed.substring(start, end + 1)
}

private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it).trim() }.filter(String::isNotEmpty)

private fun JSONArray?.toTasks(): List<MinuteTask> = if (this == null) emptyList() else (0 until length()).mapNotNull {
    val task = getJSONObject(it)
    val description = task.optString("descricao").trim()
    if (description.isEmpty()) return@mapNotNull null
    val evidence = task.optString("evidencia").trim()
    require(evidence.isNotEmpty()) { "Tarefa sem evidência" }
    MinuteTask(description, task.optString("responsavel").takeUnless { it.isBlank() }, task.optString("prazo").takeUnless { it.isBlank() }, evidence)
}
