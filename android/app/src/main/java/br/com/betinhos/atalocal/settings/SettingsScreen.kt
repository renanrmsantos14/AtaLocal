package br.com.betinhos.atalocal.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.betinhos.atalocal.data.ModelInstallDao
import kotlinx.coroutines.launch

private const val PREFS = "atalocal.settings"
private const val KEY_LANGUAGE = "transcription_language"
private const val KEY_AUTO_PROCESS = "auto_process"
private const val KEY_RETENTION_DAYS = "retention_days"
private const val KEY_TRANSCRIPT_RETENTION_DAYS = "retention_transcripts_days"
private const val KEY_MINUTES_RETENTION_DAYS = "retention_minutes_days"
private const val KEY_DEFAULT_WHISPER = "default_whisper_model"
private const val KEY_DEFAULT_LLM = "default_llm_model"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modelDao: ModelInstallDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(prefs.getString(KEY_LANGUAGE, "pt") ?: "pt") }
    var autoProcess by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_PROCESS, true)) }
    var retentionDays by remember { mutableIntStateOf(prefs.getInt(KEY_RETENTION_DAYS, 30)) }
    var transcriptRetentionDays by remember { mutableIntStateOf(prefs.getInt(KEY_TRANSCRIPT_RETENTION_DAYS, 180)) }
    var minutesRetentionDays by remember { mutableIntStateOf(prefs.getInt(KEY_MINUTES_RETENTION_DAYS, 365)) }
    var defaultWhisper by remember { mutableStateOf(prefs.getString(KEY_DEFAULT_WHISPER, null)) }
    var defaultLlm by remember { mutableStateOf(prefs.getString(KEY_DEFAULT_LLM, null)) }
    var updateState by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val installed by modelDao.observeAll().collectAsState(initial = emptyList())
    fun save(key: String, value: Any) = prefs.edit().apply {
        when (value) { is String -> putString(key, value); is Boolean -> putBoolean(key, value); is Int -> putInt(key, value) }
    }.apply()
    Scaffold(topBar = { TopAppBar(title = { Text("Configurações") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Preferências locais", style = MaterialTheme.typography.headlineMedium)
            Text("Idioma da transcrição")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pt" to "Português", "auto" to "Detectar").forEach { (value, label) ->
                    FilterChip(selected = language == value, onClick = { language = value; save(KEY_LANGUAGE, value) }, label = { Text(label) })
                }
            }
            Text("Modelo Whisper padrão")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                installed.filter { it.kind == "whisper" }.forEach { model ->
                    FilterChip(selected = defaultWhisper == model.id, onClick = { defaultWhisper = model.id; save(KEY_DEFAULT_WHISPER, model.id) }, label = { Text(model.id.substringBefore(".bin")) })
                }
            }
            Text("Modelo de ata padrão")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                installed.filter { it.kind == "llm" }.forEach { model ->
                    FilterChip(selected = defaultLlm == model.id, onClick = { defaultLlm = model.id; save(KEY_DEFAULT_LLM, model.id) }, label = { Text(model.id.substringBefore(".gguf")) })
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
            HorizontalDivider()
            Text("Aplicativo", style = MaterialTheme.typography.titleLarge)
            Text("Versão ${br.com.betinhos.atalocal.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    checkingUpdate = true
                    updateState = "Procurando uma versão mais recente…"
                    scope.launch {
                        runCatching { AppUpdater.check() }
                            .onSuccess { update ->
                                if (update == null) updateState = "Você já está usando a versão mais recente."
                                else {
                                    updateState = "Baixando a versão ${update.tag}…"
                                    runCatching { AppUpdater.downloadAndInstall(context, update) }
                                        .onFailure { updateState = "Não foi possível instalar: ${it.message ?: "erro desconhecido"}" }
                                }
                            }
                            .onFailure { updateState = "Não foi possível consultar o GitHub: ${it.message ?: "erro de conexão"}" }
                        checkingUpdate = false
                    }
                },
                enabled = !checkingUpdate,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (checkingUpdate) "Verificando…" else "Verificar atualizações") }
            updateState?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            }
        }
    }
}
