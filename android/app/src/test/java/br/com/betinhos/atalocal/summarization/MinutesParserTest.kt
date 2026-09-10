package br.com.betinhos.atalocal.summarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinutesParserTest {
    @Test fun parsesFactualStructuredMinutes() {
        val minutes = parseMinutes("""{"resumo":"Decidimos o prazo.","assuntos":["Prazo"],"decisoes":["Entregar sexta"],"tarefas":[{"descricao":"Enviar arquivo","responsavel":null,"prazo":null,"evidencia":"vamos enviar"}],"pendencias":[],"alertas":[]}""")

        assertEquals("Decidimos o prazo.", minutes.summary)
        assertNull(minutes.tasks.single().assignee)
        assertEquals("vamos enviar", minutes.tasks.single().evidence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTaskWithoutEvidence() {
        parseMinutes("""{"resumo":"x","assuntos":[],"decisoes":[],"tarefas":[{"descricao":"y","responsavel":null,"prazo":null,"evidencia":""}],"pendencias":[],"alertas":[]}""")
    }
}
