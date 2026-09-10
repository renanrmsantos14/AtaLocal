package br.com.betinhos.atalocal.summarization

import java.io.File

private const val MAX_PROMPT_TRANSCRIPT_CHARS = 9_000

fun splitTranscriptForSummary(transcript: String, maxChars: Int = MAX_PROMPT_TRANSCRIPT_CHARS): List<String> {
    require(maxChars > 0)
    if (transcript.length <= maxChars) return listOf(transcript)
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    transcript.lineSequence().forEach { line ->
        val candidateLength = current.length + if (current.isEmpty()) 0 else 1 + line.length
        if (current.isNotEmpty() && candidateLength > maxChars) {
            chunks += current.toString()
            current.clear()
        }
        if (line.length <= maxChars) {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        } else {
            line.chunked(maxChars).forEach { part ->
                if (current.isNotEmpty()) {
                    chunks += current.toString()
                    current.clear()
                }
                chunks += part
            }
        }
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks.ifEmpty { listOf(transcript.take(maxChars)) }
}

suspend fun generateMinutesInChunks(
    engine: LlamaEngine,
    model: File,
    transcript: String,
    onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
): Minutes {
    val chunks = splitTranscriptForSummary(transcript)
    val partial = chunks.mapIndexed { index, chunk ->
        val minutes = parseMinutesOrFallback(engine.generate(model, buildFactualPrompt(chunk)), chunk)
        onProgress(index + 1, chunks.size)
        minutes
    }
    return partial.mergeSummaries(chunks.size)
}

private fun List<Minutes>.mergeSummaries(chunkCount: Int): Minutes {
    val fallback = firstOrNull() ?: Minutes("A ata não possui conteúdo.", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    return Minutes(
        summary = if (size == 1) fallback.summary else joinToString("\n") { it.summary },
        participants = flatMap { it.participants }.distinct(),
        topics = flatMap { it.topics }.distinct(),
        decisions = flatMap { it.decisions }.distinct(),
        tasks = flatMap { it.tasks }.distinctBy { "${it.description}|${it.evidence}" },
        pending = flatMap { it.pending }.distinct(),
        alerts = (flatMap { it.alerts }.distinct() + if (size > 1) listOf("Ata composta por $chunkCount blocos de transcrição para caber no processamento local.") else emptyList<String>()).distinct()
    )
}
