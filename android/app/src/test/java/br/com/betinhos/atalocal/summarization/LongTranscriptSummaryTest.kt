package br.com.betinhos.atalocal.summarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTranscriptSummaryTest {
    @Test fun splitsLongTranscriptWithoutDroppingLines() {
        val source = (1..10).joinToString("\n") { "[$it] linha de transcrição" }
        val chunks = splitTranscriptForSummary(source, maxChars = 35)

        assertTrue(chunks.size > 1)
        assertEquals(source, chunks.joinToString("\n"))
        assertTrue(chunks.all { it.length <= 35 })
    }
}
