package br.com.betinhos.atalocal.export

import br.com.betinhos.atalocal.summarization.MinuteTask
import br.com.betinhos.atalocal.summarization.Minutes
import org.junit.Assert.assertTrue
import org.junit.Test

class MinutesMarkdownTest {
    @Test fun exportsAllSectionsWithoutDroppingNullFields() {
        val markdown = minutesToMarkdown(Minutes("Resumo", listOf("Prazo"), listOf("Entregar"), listOf(MinuteTask("Enviar", null, null, "fala")), listOf("Aprovação"), listOf()))

        assertTrue(markdown.contains("# Ata da reunião"))
        assertTrue(markdown.contains("## Tarefas"))
        assertTrue(markdown.contains("Responsável: não informado"))
        assertTrue(markdown.contains("Evidência: fala"))
    }
}
