package br.com.betinhos.atalocal

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.ArtifactEntity
import br.com.betinhos.atalocal.export.exportTextPdf
import br.com.betinhos.atalocal.export.sharePdf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.work.WorkManager
import br.com.betinhos.atalocal.models.selectWhisperModel
import br.com.betinhos.atalocal.pipeline.PipelineScheduler
import br.com.betinhos.atalocal.domain.MeetingStatus
import br.com.betinhos.atalocal.domain.userLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(database: AtaLocalDatabase, meetingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meeting by database.meetingDao().observe(meetingId).collectAsState(initial = null)
    val transcript by database.transcriptSegmentDao().observeAll(meetingId).collectAsState(initial = emptyList())
    val audioSegments by database.audioSegmentDao().observeAll(meetingId).collectAsState(initial = emptyList())
    val artifacts by database.artifactDao().observe(meetingId).collectAsState(initial = emptyList())
    val artifact = artifacts.firstOrNull()
    val job by database.processingJobDao().observe(meetingId).collectAsState(initial = null)
    var edited by remember(artifact?.id, artifact?.content) { mutableStateOf(artifact?.content.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var activeSection by rememberSaveable { mutableStateOf("transcript") }
    val visibleTranscript = transcript.filter { search.isBlank() || it.text.contains(search, ignoreCase = true) || it.editedText?.contains(search, ignoreCase = true) == true }
    val currentStatus = meeting?.status ?: MeetingStatus.DRAFT
    val progress = job?.progress?.coerceIn(0f, 1f) ?: 0f
    val completedAudio = job?.checkpoint?.let(::completedSegmentCountForUi) ?: 0

    Scaffold(topBar = { TopAppBar(title = { Text(meeting?.title ?: "Reunião") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Voltar") }
    }, actions = {
        TextButton(onClick = { deleteOpen = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("REUNIÃO", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.primary)
                    Text(meeting?.title ?: "Reunião", style = MaterialTheme.typography.headlineMedium)
                    StatusBadge(currentStatus)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("ÁUDIO", "${audioSegments.size}", "segmentos")
                        Metric("TEXTO", "${transcript.size}", "trechos")
                        Metric("DURAÇÃO", formatDuration(meeting?.durationSeconds ?: 0), "gravada")
                    }
                }
                if (meeting?.status == MeetingStatus.RECORDED && audioSegments.isEmpty()) {
                    Text("Nenhum arquivo de áudio foi finalizado. Grave novamente e mantenha o app aberto até aparecer a confirmação.", color = MaterialTheme.colorScheme.error)
                }
            }
            job?.let { current ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Andamento do processamento", style = MaterialTheme.typography.titleMedium)
                        Text(processingHint(current.status, current.checkpoint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ProcessingSteps(current.status)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (current.progress > 0f) "${(progress * 100).toInt()}% concluído" else "Preparando…", style = MaterialTheme.typography.labelLarge)
                            Text("${completedAudio.coerceAtMost(audioSegments.size)}/${audioSegments.size} segmentos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current.status == MeetingStatus.TRANSCRIBING && current.progress <= 0f) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { current.progress }, Modifier.fillMaxWidth())
                        }
                        current.checkpoint?.let { checkpoint ->
                            Text(checkpointLabel(checkpoint), style = MaterialTheme.typography.bodySmall)
                        }
                        current.error?.let { Text("Detalhe: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (current.status == MeetingStatus.TRANSCRIBING && current.error == null) {
                            Text("Tudo acontece neste aparelho. Não feche o app até aparecer a confirmação do segmento.", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current.status in setOf(MeetingStatus.FAILED, MeetingStatus.CANCELLED)) Button(onClick = {
                                scope.launch {
                                    if (canRegenerateSummary(current.error, transcript.isNotEmpty())) {
                                        PipelineScheduler.regenerateSummary(context, meetingId)
                                    } else {
                                        val language = context.getSharedPreferences("atalocal.settings", android.content.Context.MODE_PRIVATE).getString("transcription_language", "pt") ?: "pt"
                                        PipelineScheduler.enqueue(context, meetingId, selectWhisperModel(database.modelInstallDao().observeAll().first()), language)
                                    }
                                }
                            }) { Text(if (canRegenerateSummary(current.error, transcript.isNotEmpty())) "Gerar ata novamente" else "Tentar novamente") }
                            if (current.status in setOf(MeetingStatus.QUEUED, MeetingStatus.TRANSCRIBING, MeetingStatus.GENERATING)) OutlinedButton(onClick = {
                                WorkManager.getInstance(context).cancelUniqueWork("pipeline-$meetingId")
                                scope.launch { database.meetingDao().updateStatus(meetingId, MeetingStatus.CANCELLED) }
                            }) { Text("Cancelar") }
                        }
                    }
                }
            }
            if (meeting?.status == MeetingStatus.RECORDED && job == null) {
                item {
                    Button(onClick = {
                        scope.launch {
                            val language = context.getSharedPreferences("atalocal.settings", android.content.Context.MODE_PRIVATE).getString("transcription_language", "pt") ?: "pt"
                            database.processingJobDao().upsert(br.com.betinhos.atalocal.data.ProcessingJobEntity(meetingId, MeetingStatus.QUEUED, checkpoint = "queued"))
                            database.meetingDao().updateStatusClearingError(meetingId, MeetingStatus.QUEUED)
                            PipelineScheduler.enqueue(context, meetingId, selectWhisperModel(database.modelInstallDao().observeAll().first()), language)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Processar agora") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = activeSection == "transcript", onClick = { activeSection = "transcript" }, label = { Text("Transcrição") })
                    FilterChip(selected = activeSection == "minutes", onClick = { activeSection = "minutes" }, label = { Text("Ata") })
                }
            }
            if (activeSection == "transcript") {
            item { Text("Transcrição", style = MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar na transcrição") }, singleLine = true) }
            if (transcript.isEmpty()) item { Text("A transcrição aparecerá aqui após o processamento.") }
            items(visibleTranscript, key = { it.id }) { segment ->
                var text by remember(segment.id, segment.text, segment.editedText) { mutableStateOf(segment.editedText ?: segment.text) }
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = {
                    Text("${segment.startMs / 1000}s${if ((segment.confidence ?: 1f) < 0.6f) " · baixa confiança" else ""}")
                },
                    supportingText = if ((segment.confidence ?: 1f) < 0.6f) ({ Text("Revise este trecho antes de usar na ata.", color = MaterialTheme.colorScheme.error) }) else null,
                    trailingIcon = { TextButton(onClick = { scope.launch { database.transcriptSegmentDao().updateEditedText(segment.id, text) } }) { Text("Salvar") } })
            }
            } else {
            item { Text("Ata", style = MaterialTheme.typography.titleLarge) }
            if (artifact == null) item { Text("A ata será gerada quando os modelos Whisper e Llama estiverem instalados.") }
            if (artifact != null) {
                item { Text("Modelo usado: ${artifact.modelVersion ?: "não informado"}", style = MaterialTheme.typography.bodySmall) }
                item { OutlinedTextField(edited, { edited = it }, Modifier.fillMaxWidth(), minLines = 12, label = { Text("Conteúdo editável") }) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { PipelineScheduler.regenerateSummary(context, meetingId) } }) { Text("Regenerar") }
                    Button(onClick = { scope.launch { database.artifactDao().upsert(artifact.copy(content = edited, editedByUser = true)) } }) { Text("Salvar") }
                    OutlinedButton(onClick = {
                        val send = Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/markdown"; putExtra(Intent.EXTRA_TEXT, edited) }, "Compartilhar ata")
                        context.startActivity(send)
                    }) { Text("Markdown") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(sharePdf(context, exportTextPdf(context, edited)), "Compartilhar PDF"))
                    }) { Text("PDF") }
                } }
            }
            }
        }
    }

    if (deleteOpen) AlertDialog(
        onDismissRequest = { deleteOpen = false },
        title = { Text("Excluir reunião?") },
        text = { Text("O áudio, a transcrição e a ata desta reunião serão removidos deste aparelho. Esta ação não pode ser desfeita.") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    WorkManager.getInstance(context).cancelUniqueWork("pipeline-$meetingId")
                    WorkManager.getInstance(context).cancelUniqueWork("summary-$meetingId")
                    database.processingJobDao().deleteForMeeting(meetingId)
                    database.audioSegmentDao().deleteForMeeting(meetingId)
                    database.transcriptSegmentDao().deleteForMeeting(meetingId)
                    database.artifactDao().deleteForMeeting(meetingId)
                    database.meetingDao().delete(meetingId)
                    context.filesDir.resolve("meetings").resolve(meetingId).deleteRecursively()
                    onBack()
                }
            }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun StatusBadge(status: MeetingStatus) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Text(status.userLabel(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Metric(label: String, value: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProcessingSteps(status: MeetingStatus) {
    val steps = listOf("Áudio", "Transcrição", "Ata")
    val active = when (status) {
        MeetingStatus.QUEUED -> 0
        MeetingStatus.TRANSCRIBING -> 1
        MeetingStatus.GENERATING, MeetingStatus.READY -> 2
        else -> -1
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, step ->
            val done = active > index || status == MeetingStatus.READY
            Surface(color = if (done || active == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                Text(if (done) "Concluído" else step, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = if (done || active == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun completedSegmentCountForUi(checkpoint: String): Int = Regex("(?:segment|processing)-(\\d+)/(\\d+)").matchEntire(checkpoint)?.groupValues?.get(1)?.toIntOrNull() ?: 0

private fun formatDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun formatTimestamp(milliseconds: Long): String = "%02d:%02d".format(milliseconds / 60_000, (milliseconds / 1_000) % 60)

private fun confidenceLabel(confidence: Float?): String = when {
    confidence == null -> "CONFIRMADA"
    confidence < 0.6f -> "REVISAR"
    else -> "BOA LEITURA"
}

@Composable
private fun confidenceColor(confidence: Float?): androidx.compose.ui.graphics.Color = if ((confidence ?: 1f) < 0.6f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

private fun checkpointLabel(checkpoint: String): String = when {
    checkpoint == "queued" -> "Aguardando processamento local…"
    checkpoint == "gerando-ata" -> "Gerando a ata com base na transcrição…"
    checkpoint.startsWith("ata-bloco-") -> "Gerando ata: bloco ${checkpoint.removePrefix("ata-bloco-")}"
    checkpoint.startsWith("processing-") -> "Transcrevendo segmento ${checkpoint.removePrefix("processing-")}…"
    checkpoint.startsWith("segment-") -> "Segmento ${checkpoint.removePrefix("segment-")} concluído"
    checkpoint == "complete" -> "Processamento concluído"
    else -> checkpoint.replace('-', ' ')
}

private fun processingHint(status: MeetingStatus, checkpoint: String?): String = when {
    status == MeetingStatus.QUEUED -> "A gravação foi salva e será transcrita localmente."
    status == MeetingStatus.TRANSCRIBING -> "Whisper está convertendo o áudio em texto."
    status == MeetingStatus.GENERATING -> "A transcrição está pronta; a ata factual está sendo montada."
    status == MeetingStatus.READY -> "Transcrição e ata disponíveis para revisão."
    status == MeetingStatus.FAILED && checkpoint?.startsWith("segment-") == true -> "A falha aconteceu depois de um trecho salvo; tente novamente para continuar."
    status == MeetingStatus.FAILED -> "O processamento parou. Leia o detalhe abaixo antes de tentar novamente."
    else -> ""
}

private fun canRegenerateSummary(error: String?, hasTranscript: Boolean): Boolean = hasTranscript && error?.let {
    it.contains("LLM", ignoreCase = true) || it.contains("Llama", ignoreCase = true) || it.contains("ata", ignoreCase = true)
} == true
