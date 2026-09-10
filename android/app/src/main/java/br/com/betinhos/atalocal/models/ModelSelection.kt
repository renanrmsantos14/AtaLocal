package br.com.betinhos.atalocal.models

import br.com.betinhos.atalocal.data.ModelInstallEntity
import java.io.File

fun selectWhisperModel(models: List<ModelInstallEntity>): String? = models.asSequence()
    .filter { it.kind == "whisper" }
    .map { File(it.filePath) }
    .firstOrNull(File::isFile)
    ?.absolutePath
