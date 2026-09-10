package br.com.betinhos.atalocal.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val PREFS = "atalocal.settings"
private const val KEY_LANGUAGE = "transcription_language"
private const val KEY_AUTO_PROCESS = "auto_process"
private const val KEY_RETENTION_DAYS = "retention_days"
private const val KEY_TRANSCRIPT_RETENTION_DAYS = "retention_transcripts_days"
private const val KEY_MINUTES_RETENTION_DAYS = "retention_minutes_days"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(prefs.getString(KEY_LANGUAGE, "pt") ?: "pt") }
    var autoProcess by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_PROCESS, true)) }
    var retentionDays by remember { mutableIntStateOf(prefs.getInt(KEY_RETENTION_DAYS, 30)) }
    var transcriptRetentionDays by remember { mutableIntStateOf(prefs.getInt(KEY_TRANSCRIPT_RETENTION_DAYS, 180)) }
    var minutesRetentionDays by remember { mutableIntStateOf(prefs.getInt(KEY_MINUTES_RETENTION_DAYS, 365)) }
    fun save(key: String, value: Any) = prefs.edit().apply {
        when (value) { is String -> putString(key, value); is Boolean -> putBoolean(key, value); is Int -> putInt(key, value) }
    }.apply()
    Scaffold(topBar = { TopAppBar(title = { Text("Configurações") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Preferências locais", style = MaterialTheme.typography.headlineMedium)
            Text("Idioma da transcrição")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pt" to "Português", "auto" to "Detectar").forEach { (value, label) ->
                    FilterChip(selected = language == value, onClick = { language = value; save(KEY_LANGUAGE, value) }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Processar automaticamente")
                Switch(checked = autoProcess, onCheckedChange = { autoProcess = it; save(KEY_AUTO_PROCESS, it) })
            }
            Text("Retenção de áudio: $retentionDays dias")
            Slider(value = retentionDays.toFloat(), onValueChange = { retentionDays = it.toInt() }, valueRange = 1f..180f, steps = 178,
                onValueChangeFinished = { save(KEY_RETENTION_DAYS, retentionDays) })
            Text("A retenção controla a limpeza futura dos arquivos de áudio; reuniões e atas não são apagadas.", style = MaterialTheme.typography.bodySmall)
            Text("Retenção da transcrição: $transcriptRetentionDays dias")
            Slider(value = transcriptRetentionDays.toFloat(), onValueChange = { transcriptRetentionDays = it.toInt() }, valueRange = 30f..365f, steps = 334,
                onValueChangeFinished = { save(KEY_TRANSCRIPT_RETENTION_DAYS, transcriptRetentionDays) })
            Text("Retenção da ata: $minutesRetentionDays dias")
            Slider(value = minutesRetentionDays.toFloat(), onValueChange = { minutesRetentionDays = it.toInt() }, valueRange = 30f..730f, steps = 699,
                onValueChangeFinished = { save(KEY_MINUTES_RETENTION_DAYS, minutesRetentionDays) })
            Text("A limpeza de transcrições e atas mantém a reunião registrada, mas remove o conteúdo vencido.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
