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
}
