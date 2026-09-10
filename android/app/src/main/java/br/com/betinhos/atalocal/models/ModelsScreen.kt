package br.com.betinhos.atalocal.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
fun ModelsScreen(dao: ModelInstallDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0L to 0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<ModelSpec?>(null) }

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onBack) { Text("Voltar") }
        Text("Modelos locais", style = MaterialTheme.typography.headlineMedium)
        Text("Baixados no armazenamento privado e verificados por SHA-256.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AndroidModelCatalog.all) { spec ->
                val model = installed.find { it.id == spec.id }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(spec.id, style = MaterialTheme.typography.titleMedium)
                        Text("${spec.kind} · ${spec.sizeBytes / 1_000_000} MB")
                        Text(if (model == null) "Não instalado" else "Instalado")
                        Button(enabled = downloading == null, onClick = {
                            error = null
                            downloading = spec.id
                            scope.launch {
                                runCatching {
                                    val file = ModelDownloader(context.filesDir.resolve("models")).download(spec) { done, total -> progress = done to total }
                                    dao.upsert(br.com.betinhos.atalocal.data.ModelInstallEntity(spec.id, spec.kind, spec.version, file.path, spec.sizeBytes, spec.sha256))
                                }.onFailure { error = it.message ?: "Falha ao baixar ${spec.id}" }
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
                    installed.find { it.id == spec.id }?.let { File(it.filePath).delete() }
                    dao.delete(spec.id)
                    removeTarget = null
                }
            }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancelar") } })
    }
}
