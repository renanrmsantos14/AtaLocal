package br.com.betinhos.atalocal.summarization

fun buildFactualPrompt(transcript: String): String = """
    Você é um assistente de atas factuais. Use somente a transcrição abaixo.
    Não invente nomes, datas, responsáveis, prazos, decisões ou participantes.
    Quando um dado não existir, use null ou uma lista vazia.
    Cada tarefa deve conter evidência literal ou paráfrase fiel da transcrição.
    Responda somente um JSON válido, sem markdown, neste formato:
    {"resumo":"","assuntos":[],"decisoes":[],"tarefas":[{"descricao":"","responsavel":null,"prazo":null,"evidencia":""}],"pendencias":[],"alertas":[]}

    TRANSCRIÇÃO:
    $transcript
""".trimIndent()
