package br.com.betinhos.atalocal.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.betinhos.atalocal.data.ModelInstallDao
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(modelDao: ModelInstallDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val models by modelDao.observeAll().collectAsState(initial = emptyList())
    val snapshot = remember(models) { collectDiagnostics(context, models, null) }
    val report = buildString {
        appendLine("AtaLocal ${snapshot.appVersion}")
        appendLine("Android ${snapshot.androidVersion}")
        appendLine("Armazenamento disponível: ${formatBytes(snapshot.availableStorageBytes)}")
        appendLine("Modelos instalados: ${snapshot.installedModels.joinToString { it.id }}")
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Diagnóstico") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Estado local", style = MaterialTheme.typography.headlineMedium)
            Text("Versão: ${snapshot.appVersion}")
            Text("Android: ${snapshot.androidVersion}")
            Text("Armazenamento disponível: ${formatBytes(snapshot.availableStorageBytes)}")
            Text("Modelos instalados: ${models.size}")
            models.forEach { Text("• ${it.id} (${formatBytes(it.sizeBytes)})") }
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico AtaLocal", report))
            }) { Text("Copiar diagnóstico") }
        }
    }
}
