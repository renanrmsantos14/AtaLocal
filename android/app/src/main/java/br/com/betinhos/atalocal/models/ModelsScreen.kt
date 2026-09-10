package br.com.betinhos.atalocal.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.betinhos.atalocal.data.ModelInstallDao
import kotlinx.coroutines.launch
import java.io.File
import br.com.betinhos.atalocal.data.ModelInstallEntity

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ModelsScreen(dao: ModelInstallDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0L to 0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<ModelSpec?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Modelos locais") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Privacidade por padrão", style = MaterialTheme.typography.headlineMedium)
                Text("Modelos ficam no armazenamento privado e são verificados por SHA-256.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            items(AndroidModelCatalog.all) { spec ->
                val model = installed.find { it.id == spec.id }
                Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(spec.id, style = MaterialTheme.typography.titleMedium)
                        Text("${spec.kind} · ${spec.sizeBytes / 1_000_000} MB")
                        Text(when (model?.status) {
                            "DOWNLOADING" -> "Download em andamento"
                            "FAILED" -> "Falha: ${model?.error ?: "tente novamente"}"
                            "INSTALLED" -> "Instalado"
                            else -> "Não instalado"
                        })
                        if (model?.status == "DOWNLOADING" && downloading == spec.id) {
                            val total = progress.second.takeIf { it > 0 } ?: spec.sizeBytes
                            LinearProgressIndicator(
                                progress = { (progress.first.toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${formatModelBytes(progress.first)} de ${formatModelBytes(total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (spec.id == AndroidModelCatalog.whisperTiny.id) {
                            Text("Recomendado para começar: menor download e mais rápido no celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(enabled = downloading == null, onClick = {
                            error = null
                            downloading = spec.id
                            scope.launch {
                                val modelDirectory = context.filesDir.resolve("models")
                                val target = modelDirectory.resolve(spec.id)
                                val current = installed.find { it.id == spec.id }
                                dao.upsert(current?.copy(status = "DOWNLOADING", downloadedBytes = modelDirectory.resolve("${spec.id}.download").length(), error = null)
                                    ?: ModelInstallEntity(spec.id, spec.kind, spec.version, target.path, spec.sizeBytes, spec.sha256, status = "DOWNLOADING", downloadedBytes = modelDirectory.resolve("${spec.id}.download").length()))
                                runCatching {
                                    val file = ModelDownloader(modelDirectory).download(spec) { done, total -> progress = done to total }
                                    dao.upsert(ModelInstallEntity(spec.id, spec.kind, spec.version, file.path, spec.sizeBytes, spec.sha256, status = "INSTALLED", downloadedBytes = spec.sizeBytes))
                                }.onFailure {
                                    error = it.message ?: "Falha ao baixar ${spec.id}"
                                    dao.upsert(ModelInstallEntity(spec.id, spec.kind, spec.version, target.path, spec.sizeBytes, spec.sha256, status = "FAILED", downloadedBytes = modelDirectory.resolve("${spec.id}.download").length(), error = error))
                                }
                                downloading = null
                            }
                        }) { Text(if (downloading == spec.id) "Baixando ${progress.first}/${progress.second}" else "Baixar / atualizar") }
                        if (model != null) TextButton(enabled = downloading == null, onClick = { removeTarget = spec }) { Text("Remover modelo") }
                    }
                }
            }
        }
    }
    removeTarget?.let { spec ->
        AlertDialog(onDismissRequest = { removeTarget = null }, title = { Text("Remover modelo?") },
            text = { Text("A reunião e a transcrição não serão apagadas. O modelo ${spec.id} será removido do aparelho.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    installed.find { it.id == spec.id }?.let {
                        File(it.filePath).delete()
                        File(it.filePath + ".download").delete()
                    }
                    dao.delete(spec.id)
                    removeTarget = null
                }
            }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancelar") } })
    }
}

private fun formatModelBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "${bytes / 1_000} KB"
}
