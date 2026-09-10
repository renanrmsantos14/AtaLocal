package br.com.betinhos.atalocal.summarization

import org.json.JSONArray
import org.json.JSONObject

data class MinuteTask(val description: String, val assignee: String?, val due: String?, val evidence: String)
data class Minutes(val summary: String, val topics: List<String>, val decisions: List<String>, val tasks: List<MinuteTask>, val pending: List<String>, val alerts: List<String>)

fun parseMinutes(raw: String): Minutes {
    val json = JSONObject(raw)
    fun strings(key: String) = json.optJSONArray(key).toStrings()
    val tasks = json.optJSONArray("tarefas").toTasks()
    return Minutes(
        summary = json.optString("resumo").trim().also { require(it.isNotEmpty()) { "Resumo ausente" } },
        topics = strings("assuntos"),
        decisions = strings("decisoes"),
        tasks = tasks,
        pending = strings("pendencias"),
        alerts = strings("alertas")
    )
}

private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it).trim() }.filter(String::isNotEmpty)

private fun JSONArray?.toTasks(): List<MinuteTask> = if (this == null) emptyList() else List(length()) {
    val task = getJSONObject(it)
    val evidence = task.optString("evidencia").trim()
    require(evidence.isNotEmpty()) { "Tarefa sem evidência" }
    MinuteTask(task.optString("descricao").trim(), task.optString("responsavel").takeUnless { it.isBlank() }, task.optString("prazo").takeUnless { it.isBlank() }, evidence)
}
