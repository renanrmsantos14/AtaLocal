package br.com.betinhos.atalocal.audio

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavValidationTest {
    @Test fun rejectsCorruptedWavHeader() {
        val file = File.createTempFile("atalocal", ".wav")
        try {
            file.writeBytes(ByteArray(64) { 1 })
            assertFalse(isValidPcm16MonoWav(file))
        } finally { file.delete() }
    }

    @Test fun acceptsWriterFormat() {
        val file = File.createTempFile("atalocal", ".wav")
        try {
            WavSegmentWriter(file).apply {
                open()
                write(ShortArray(160), 160)
                close()
            }
            assertTrue(isValidPcm16MonoWav(file))
        } finally { file.delete() }
    }
}
