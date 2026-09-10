package br.com.betinhos.atalocal.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.betinhos.atalocal.data.ModelInstallDao
import br.com.betinhos.atalocal.data.MeetingDao
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(modelDao: ModelInstallDao, meetingDao: MeetingDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val models by modelDao.observeAll().collectAsState(initial = emptyList())
    val meetings by meetingDao.observeAll().collectAsState(initial = emptyList())
    val snapshot = remember(models, meetings) { collectDiagnostics(context, models, meetings.firstNotNullOfOrNull { it.error }) }
    val installedBytes = models.filter { it.status == "INSTALLED" }.sumOf { File(it.filePath).takeIf(File::isFile)?.length() ?: 0L }
    val report = buildString {
        appendLine("AtaLocal ${snapshot.appVersion}")
        appendLine("Android ${snapshot.androidVersion}")
        appendLine("Armazenamento disponível: ${formatBytes(snapshot.availableStorageBytes)}")
        appendLine("Espaço usado pelos modelos: ${formatBytes(installedBytes)}")
        appendLine("Modelos instalados: ${snapshot.installedModels.joinToString { it.id }}")
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Diagnóstico") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Estado local", style = MaterialTheme.typography.headlineMedium)
            Text("Versão: ${snapshot.appVersion}")
            Text("Android: ${snapshot.androidVersion}")
            Text("Armazenamento disponível: ${formatBytes(snapshot.availableStorageBytes)}")
            Text("Espaço usado pelos modelos: ${formatBytes(installedBytes)}")
            Text("Modelos instalados: ${models.count { it.status == "INSTALLED" && File(it.filePath).isFile }} de ${models.size}")
            snapshot.lastError?.let { Text("Último erro: $it", color = MaterialTheme.colorScheme.error) }
            models.forEach { model ->
                val state = when (model.status) {
                    "INSTALLED" -> "instalado"
                    "DOWNLOADING" -> "baixando ${formatBytes(model.downloadedBytes)} de ${formatBytes(model.sizeBytes)}"
                    "FAILED" -> "falhou: ${model.error ?: "erro desconhecido"}"
                    else -> model.status.lowercase()
                }
                Text("• ${model.id} (${formatBytes(model.sizeBytes)}) — $state")
            }
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico AtaLocal", report))
            }) { Text("Copiar diagnóstico") }
            OutlinedButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                }, "Compartilhar diagnóstico"))
            }) { Text("Compartilhar diagnóstico") }
        }
    }
}
