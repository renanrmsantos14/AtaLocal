package br.com.betinhos.atalocal.models

import br.com.betinhos.atalocal.data.ModelInstallEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ModelSelectionTest {
    @Test fun `seleciona whisper instalado`() {
        val file = File.createTempFile("whisper", ".bin")
        try {
            assertEquals(file.absolutePath, selectWhisperModel(listOf(
                ModelInstallEntity("w", "whisper", "1", file.path, 1, "hash")
            )))
        } finally { file.delete() }
    }

    @Test fun `retorna nulo sem whisper valido`() {
        assertNull(selectWhisperModel(emptyList()))
    }

    @Test fun `prioriza modelo padrão instalado`() {
        val first = File.createTempFile("whisper-first", ".bin")
        val preferred = File.createTempFile("whisper-preferred", ".bin")
        try {
            val models = listOf(
                ModelInstallEntity("first", "whisper", "1", first.path, 1, "hash"),
                ModelInstallEntity("preferred", "whisper", "1", preferred.path, 1, "hash")
            )
            assertEquals(preferred.absolutePath, selectWhisperModel(models, "preferred"))
        } finally { first.delete(); preferred.delete() }
    }

    @Test fun `ignora modelo com download falho mesmo com arquivo no caminho`() {
        val file = File.createTempFile("whisper-failed", ".bin")
        try {
            assertNull(selectWhisperModel(listOf(
                ModelInstallEntity("failed", "whisper", "1", file.path, 1, "hash", status = "FAILED")
            )))
        } finally { file.delete() }
    }
}
