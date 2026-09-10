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
    val artifacts by database.artifactDao().observe(meetingId).collectAsState(initial = emptyList())
    val artifact = artifacts.firstOrNull()
    val job by database.processingJobDao().observe(meetingId).collectAsState(initial = null)
    var edited by remember(artifact?.id, artifact?.content) { mutableStateOf(artifact?.content.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    val visibleTranscript = transcript.filter { search.isBlank() || it.text.contains(search, ignoreCase = true) }

    Scaffold(topBar = { TopAppBar(title = { Text(meeting?.title ?: "Reunião") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Voltar") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { Text(meeting?.status?.userLabel() ?: "", style = MaterialTheme.typography.labelLarge) }
            job?.let { current ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.status.userLabel(), style = MaterialTheme.typography.titleMedium)
                        if (current.status == MeetingStatus.TRANSCRIBING && current.progress <= 0f) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { current.progress }, Modifier.fillMaxWidth())
                        }
                        current.checkpoint?.takeIf { it.startsWith("segment-") }?.let {
                            Text("Segmento $it", style = MaterialTheme.typography.bodySmall)
                        }
                        current.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (current.status == MeetingStatus.TRANSCRIBING && current.error == null) {
                            Text("O áudio está sendo processado localmente. Isso pode levar alguns minutos em modelos maiores.", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current.status in setOf(MeetingStatus.FAILED, MeetingStatus.CANCELLED)) Button(onClick = {
                                scope.launch {
                                    val language = context.getSharedPreferences("atalocal.settings", android.content.Context.MODE_PRIVATE).getString("transcription_language", "pt") ?: "pt"
                                    database.processingJobDao().upsert(br.com.betinhos.atalocal.data.ProcessingJobEntity(meetingId, MeetingStatus.QUEUED, checkpoint = "queued"))
                                    database.meetingDao().updateStatus(meetingId, MeetingStatus.QUEUED)
                                    PipelineScheduler.enqueue(context, meetingId, selectWhisperModel(database.modelInstallDao().observeAll().first()), language)
                                }
                            }) { Text("Tentar novamente") }
                            if (current.status in setOf(MeetingStatus.QUEUED, MeetingStatus.TRANSCRIBING, MeetingStatus.GENERATING)) OutlinedButton(onClick = {
                                WorkManager.getInstance(context).cancelUniqueWork("pipeline-$meetingId")
                                scope.launch { database.meetingDao().updateStatus(meetingId, MeetingStatus.CANCELLED) }
                            }) { Text("Cancelar") }
                        }
                    }
                }
            }
            item { Text("Transcrição", style = MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar na transcrição") }, singleLine = true) }
            if (transcript.isEmpty()) item { Text("A transcrição aparecerá aqui após o processamento.") }
            items(visibleTranscript, key = { it.id }) { segment ->
                var text by remember(segment.id, segment.text) { mutableStateOf(segment.text) }
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("${segment.startMs / 1000}s") },
                    trailingIcon = { TextButton(onClick = { scope.launch { database.transcriptSegmentDao().updateText(segment.id, text) } }) { Text("Salvar") } })
            }
            item { Text("Ata", style = MaterialTheme.typography.titleLarge) }
            if (artifact == null) item { Text("A ata será gerada quando os modelos Whisper e Llama estiverem instalados.") }
            if (artifact != null) {
                item { OutlinedTextField(edited, { edited = it }, Modifier.fillMaxWidth(), minLines = 12, label = { Text("Conteúdo editável") }) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { PipelineScheduler.regenerateSummary(context, meetingId) }) { Text("Regenerar") }
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
