package br.com.betinhos.atalocal.summarization

import org.junit.Assert.assertTrue
import org.junit.Test

class FactualPromptTest {
    @Test fun includesTranscriptAndAntiInferenceRules() {
        val prompt = buildFactualPrompt("[00:00] Renan: enviar contrato sexta")

        assertTrue(prompt.contains("enviar contrato sexta"))
        assertTrue(prompt.contains("Não invente"))
        assertTrue(prompt.contains("JSON"))
    }
}
