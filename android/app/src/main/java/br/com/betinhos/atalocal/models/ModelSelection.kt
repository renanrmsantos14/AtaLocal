package br.com.betinhos.atalocal.models

import br.com.betinhos.atalocal.data.ModelInstallEntity
import java.io.File

fun selectWhisperModel(models: List<ModelInstallEntity>, preferredId: String? = null): String? = models.asSequence()
    .filter { it.kind == "whisper" }
    .sortedByDescending { it.id == preferredId }
    .map { File(it.filePath) }
    .firstOrNull(File::isFile)
    ?.absolutePath

fun selectModel(models: List<ModelInstallEntity>, kind: String, preferredId: String? = null): String? = models.asSequence()
    .filter { it.kind == kind }
    .sortedByDescending { it.id == preferredId }
    .map { File(it.filePath) }
    .firstOrNull(File::isFile)
    ?.absolutePath
