package br.com.betinhos.atalocal.summarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinutesParserTest {
    @Test fun parsesFactualStructuredMinutes() {
        val minutes = parseMinutes("""{"resumo":"Decidimos o prazo.","participantes":["Renan"],"assuntos":["Prazo"],"decisoes":["Entregar sexta"],"tarefas":[{"descricao":"Enviar arquivo","responsavel":null,"prazo":null,"evidencia":"vamos enviar"}],"pendencias":[],"alertas":[]}""")

        assertEquals("Decidimos o prazo.", minutes.summary)
        assertEquals(listOf("Renan"), minutes.participants)
        assertNull(minutes.tasks.single().assignee)
        assertEquals("vamos enviar", minutes.tasks.single().evidence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTaskWithoutEvidence() {
        parseMinutes("""{"resumo":"x","assuntos":[],"decisoes":[],"tarefas":[{"descricao":"y","responsavel":null,"prazo":null,"evidencia":""}],"pendencias":[],"alertas":[]}""")
    }

    @Test fun invalidModelOutputGetsSafeFactualFallback() {
        val minutes = parseMinutesOrFallback("not-json")

        assertEquals("A ata automática não pôde ser estruturada.", minutes.summary)
        assertTrue(minutes.alerts.single().contains("JSON inválido"))
        assertTrue(minutes.topics.isEmpty())
    }

    @Test fun parsesJsonWrappedInMarkdownFence() {
        val minutes = parseMinutes(
            """```json
            {"resumo":"Ata pronta.","participantes":[],"assuntos":[],"decisoes":[],"tarefas":[],"pendencias":[],"alertas":[]}
            ```""".trimIndent()
        )

        assertEquals("Ata pronta.", minutes.summary)
    }

    @Test fun parsesJsonAfterModelReasoning() {
        val minutes = parseMinutes(
            """<think>Vou organizar a resposta.</think>
            {"resumo":"Ata pronta.","participantes":[],"assuntos":[],"decisoes":[],"tarefas":[],"pendencias":[],"alertas":[]}""".trimIndent()
        )

        assertEquals("Ata pronta.", minutes.summary)
    }

    @Test fun removesParticipantsAndTasksWithoutTranscriptEvidence() {
        val minutes = parseMinutesOrFallback(
            """{"resumo":"x","participantes":["Pessoa inventada","Renan"],"assuntos":[],"decisoes":[],"tarefas":[{"descricao":"inventada","responsavel":null,"prazo":null,"evidencia":"frase que não existe"},{"descricao":"Enviar contrato","responsavel":null,"prazo":null,"evidencia":"enviar contrato"}],"pendencias":[],"alertas":[]}""",
            "Renan disse: vamos enviar contrato amanhã."
        )

        assertEquals(listOf("Renan"), minutes.participants)
        assertEquals(1, minutes.tasks.size)
        assertEquals("Enviar contrato", minutes.tasks.single().description)
        assertTrue(minutes.alerts.any { it.contains("evidência") })
    }
}
