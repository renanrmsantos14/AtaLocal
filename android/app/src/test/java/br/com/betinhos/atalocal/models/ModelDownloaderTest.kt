package br.com.betinhos.atalocal.models

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class ModelDownloaderTest {
    @Test fun replacesExistingModelOnlyAfterValidatedDownload() {
        val directory = Files.createTempDirectory("atalocal-model").toFile()
        val partial = directory.resolve("whisper.bin.download")
        val target = directory.resolve("whisper.bin")
        partial.writeText("modelo novo validado")
        target.writeText("modelo antigo")

        ModelDownloader(directory).installAtomically(partial, target)

        assertEquals("modelo novo validado", target.readText())
        assertFalse(partial.exists())
        target.delete()
        directory.delete()
    }

    @Test fun keepsExistingModelWhenHashValidationFailsBeforeInstall() {
        val directory = Files.createTempDirectory("atalocal-model").toFile()
        val partial = directory.resolve("whisper.bin.download")
        val target = directory.resolve("whisper.bin")
        partial.writeText("download incompleto")
        target.writeText("modelo antigo")

        val spec = ModelSpec("whisper.bin", "whisper", "1", "http://localhost/model", "00".repeat(32), partial.length())
        assertThrows(IllegalStateException::class.java) {
            ModelDownloader(directory).validateDownloadedFile(partial, spec)
        }
        assertEquals("modelo antigo", target.readText())
        assertTrue(partial.exists())
        directory.deleteRecursively()
    }
}
