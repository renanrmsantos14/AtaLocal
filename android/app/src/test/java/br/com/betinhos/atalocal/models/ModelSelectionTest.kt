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

    @Test fun `prefere base como padrão e cai para tiny`() {
        val tiny = File.createTempFile("whisper-tiny", ".bin")
        val base = File.createTempFile("whisper-base", ".bin")
        try {
            val models = listOf(
                ModelInstallEntity("whisper-tiny-q5_1.bin", "whisper", "1", tiny.path, 1, "hash"),
                ModelInstallEntity("whisper-base-q5_1.bin", "whisper", "1", base.path, 1, "hash")
            )
            assertEquals(base.absolutePath, selectWhisperModel(models))
            assertEquals(tiny.absolutePath, selectWhisperModel(listOf(
                ModelInstallEntity("whisper-tiny-q5_1.bin", "whisper", "1", tiny.path, 1, "hash")
            )))
        } finally { tiny.delete(); base.delete() }
    }

    @Test fun `prefere LLM menor quando não há preferência configurada`() {
        val small = File.createTempFile("qwen-small", ".gguf")
        val large = File.createTempFile("qwen-large", ".gguf")
        try {
            assertEquals(small.absolutePath, selectModel(listOf(
                ModelInstallEntity("Qwen3-4B-Instruct-Q4.gguf", "llm", "1", large.path, 1, "hash"),
                ModelInstallEntity("qwen2.5-1.5b-instruct-q4_k_m.gguf", "llm", "1", small.path, 1, "hash")
            ), "llm"))
        } finally { small.delete(); large.delete() }
    }
}
