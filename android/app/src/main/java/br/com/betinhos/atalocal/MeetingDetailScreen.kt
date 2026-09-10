package br.com.betinhos.atalocal

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.ArtifactEntity
import br.com.betinhos.atalocal.export.exportTextPdf
import br.com.betinhos.atalocal.export.sharePdf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(database: AtaLocalDatabase, meetingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meeting by database.meetingDao().observe(meetingId).collectAsState(initial = null)
    val transcript by database.transcriptSegmentDao().observeAll(meetingId).collectAsState(initial = emptyList())
    val artifacts by database.artifactDao().observe(meetingId).collectAsState(initial = emptyList())
    val artifact = artifacts.firstOrNull()
    var edited by remember(artifact?.id, artifact?.content) { mutableStateOf(artifact?.content.orEmpty()) }

    Scaffold(topBar = { TopAppBar(title = { Text(meeting?.title ?: "Reunião") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Voltar") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { Text(meeting?.status?.name ?: "", style = MaterialTheme.typography.labelLarge) }
            item { Text("Transcrição", style = MaterialTheme.typography.titleLarge) }
            if (transcript.isEmpty()) item { Text("A transcrição aparecerá aqui após o processamento.") }
            items(transcript) { Text("${it.startMs / 1000}s  ${it.text}") }
            item { Text("Ata", style = MaterialTheme.typography.titleLarge) }
            if (artifact == null) item { Text("A ata será gerada quando os modelos Whisper e Llama estiverem instalados.") }
            if (artifact != null) {
                item { OutlinedTextField(edited, { edited = it }, Modifier.fillMaxWidth(), minLines = 12, label = { Text("Conteúdo editável") }) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
