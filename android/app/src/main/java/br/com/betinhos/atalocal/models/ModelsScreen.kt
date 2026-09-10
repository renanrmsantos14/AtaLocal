package br.com.betinhos.atalocal.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.launch
import java.io.File
import br.com.betinhos.atalocal.data.ModelInstallEntity

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ModelsScreen(dao: ModelInstallDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<ModelSpec?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Modelos locais") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Privacidade por padrão", style = MaterialTheme.typography.headlineMedium)
                Text("Modelos ficam no armazenamento privado e são verificados por SHA-256.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(2.dp))
                val readyCount = AndroidModelCatalog.all.count { spec -> installed.find { it.id == spec.id }?.let(::isUsableModel) == true }
                val activeCount = AndroidModelCatalog.all.count { spec -> installed.any { it.id == spec.id && it.status == "DOWNLOADING" } }
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        ModelSummary("PRONTOS", "$readyCount/${AndroidModelCatalog.all.size}")
                        ModelSummary("ATIVOS", activeCount.toString())
                        ModelSummary("PRIVACIDADE", "100% local")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            items(AndroidModelCatalog.all) { spec ->
                val model = installed.find { it.id == spec.id }
                val work by WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("model-${spec.id}").collectAsState(initial = emptyList())
                val activeWork = work.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                val workProgress = activeWork?.progress
                val doneBytes = workProgress?.getLong(ModelDownloadWorker.KEY_DONE, model?.downloadedBytes ?: 0L) ?: (model?.downloadedBytes ?: 0L)
                val totalBytes = workProgress?.getLong(ModelDownloadWorker.KEY_TOTAL, spec.sizeBytes) ?: spec.sizeBytes
                val usable = model?.let(::isUsableModel) == true
                Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(spec.id, style = MaterialTheme.typography.titleMedium)
                        Text("${spec.kind} · ${spec.sizeBytes / 1_000_000} MB")
                        Text(when (model?.status) {
                            "DOWNLOADING" -> "Download em andamento"
                            "FAILED" -> "Falha: ${model?.error ?: "tente novamente"}"
                            "INSTALLED" -> if (isUsableModel(requireNotNull(model))) "Instalado" else "Arquivo inválido — baixe novamente"
                            else -> "Não instalado"
                        })
                        if (activeWork != null || model?.status == "DOWNLOADING") {
                            LinearProgressIndicator(
                                progress = { (doneBytes.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${formatModelBytes(doneBytes)} de ${formatModelBytes(totalBytes)} · continua em segundo plano", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (spec.id == AndroidModelCatalog.whisperTiny.id) {
                            Text("Recomendado para começar: menor download e mais rápido no celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(enabled = !usable && activeWork == null, onClick = {
                            error = null
                            scope.launch {
                                val current = installed.find { it.id == spec.id }
                                if (current?.let(::isUsableModel) == true) return@launch
                                val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(workDataOf(ModelDownloadWorker.KEY_ID to spec.id)).build()
                                WorkManager.getInstance(context).enqueueUniqueWork("model-${spec.id}", ExistingWorkPolicy.KEEP, request)
                            }
                        }) { Text(if (usable) "Já instalado" else if (activeWork != null) "Baixando…" else "Baixar / atualizar") }
                        if (model != null && activeWork == null) TextButton(onClick = { removeTarget = spec }) { Text("Remover modelo") }
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

@Composable
private fun ModelSummary(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun formatModelBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "${bytes / 1_000} KB"
}
