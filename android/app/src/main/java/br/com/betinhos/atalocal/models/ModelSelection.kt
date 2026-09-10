package br.com.betinhos.atalocal.models

import br.com.betinhos.atalocal.data.ModelInstallEntity
import java.io.File

fun selectWhisperModel(models: List<ModelInstallEntity>, preferredId: String? = null): String? = models.asSequence()
    .filter { it.kind == "whisper" }
    .filter { it.status == "INSTALLED" }
    .sortedWith(compareBy<ModelInstallEntity>({ it.id != preferredId }, { whisperRank(it.id) }))
    .map { File(it.filePath) }
    .firstOrNull(File::isFile)
    ?.absolutePath

private fun whisperRank(id: String): Int = when {
    id.contains("base", ignoreCase = true) -> 0
    id.contains("tiny", ignoreCase = true) -> 1
    id.contains("small", ignoreCase = true) -> 2
    id.contains("large", ignoreCase = true) -> 3
    else -> 4
}

fun selectModel(models: List<ModelInstallEntity>, kind: String, preferredId: String? = null): String? = models.asSequence()
    .filter { it.kind == kind }
    .filter { it.status == "INSTALLED" }
    .sortedWith(compareBy<ModelInstallEntity>({ it.id != preferredId }, { modelRank(it.id, kind) }))
    .map { File(it.filePath) }
    .firstOrNull(File::isFile)
    ?.absolutePath

private fun modelRank(id: String, kind: String): Int = if (kind == "llm") {
    when {
        id.contains("1.5b", ignoreCase = true) -> 0
        id.contains("1.7b", ignoreCase = true) -> 1
        id.contains("4b", ignoreCase = true) -> 2
        else -> 3
    }
} else 0
