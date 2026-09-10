package br.com.betinhos.atalocal

import android.os.Bundle
import android.os.Build
import android.os.StatFs
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.MeetingDao
import br.com.betinhos.atalocal.data.ModelInstallDao
import br.com.betinhos.atalocal.pipeline.PipelineScheduler
import br.com.betinhos.atalocal.models.ModelsScreen
import br.com.betinhos.atalocal.models.selectWhisperModel
import br.com.betinhos.atalocal.domain.userLabel
import br.com.betinhos.atalocal.diagnostics.DiagnosticsScreen
import br.com.betinhos.atalocal.diagnostics.formatBytes
import br.com.betinhos.atalocal.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import br.com.betinhos.atalocal.settings.RetentionPolicy
import br.com.betinhos.atalocal.settings.cleanupExpiredAudio
import br.com.betinhos.atalocal.settings.cleanupExpiredDerivedData
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.pipeline.PipelineRecovery

class MainActivity : ComponentActivity() {
    private var activeMeetingId: String? = null
    private var recordingStartedAt: Long = 0L
    private lateinit var meetingDao: MeetingDao
    private lateinit var modelInstallDao: ModelInstallDao
    private var recordingUiMeetingId by mutableStateOf<String?>(null)
    private var recordingUiStartedAt by mutableStateOf(0L)
    private var microphoneLevel by mutableStateOf(0f)
    private var openMeetingAfterStop by mutableStateOf<String?>(null)
    private val levelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { microphoneLevel = intent.getFloatExtra(br.com.betinhos.atalocal.audio.RecordingService.EXTRA_LEVEL, 0f) }
    }
    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(br.com.betinhos.atalocal.audio.RecordingService.EXTRA_MEETING_ID) ?: return
            if (getSharedPreferences("atalocal.settings", MODE_PRIVATE).getBoolean("auto_process", true)) {
                lifecycleScope.launch {
                    val preferredWhisper = getSharedPreferences("atalocal.settings", MODE_PRIVATE).getString("default_whisper_model", null)
                    val modelPath = selectWhisperModel(modelInstallDao.observeAll().first(), preferredWhisper)
                    val language = getSharedPreferences("atalocal.settings", MODE_PRIVATE).getString("transcription_language", "pt") ?: "pt"
                    PipelineScheduler.enqueue(this@MainActivity, id, modelPath, language)
                }
            }
        }
    }
    private val recordingErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(br.com.betinhos.atalocal.audio.RecordingService.EXTRA_MEETING_ID) ?: return
            val message = intent.getStringExtra(br.com.betinhos.atalocal.audio.RecordingService.EXTRA_ERROR) ?: "Não foi possível iniciar a gravação"
            lifecycleScope.launch { meetingDao.updateStatus(id, br.com.betinhos.atalocal.domain.MeetingStatus.FAILED, error = message) }
            recordingUiMeetingId = null
            openMeetingAfterStop = id
        }
    }
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingService()
        else recordingUiMeetingId = null
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = DatabaseProvider.get(applicationContext)
        meetingDao = database.meetingDao()
        modelInstallDao = database.modelInstallDao()
        ContextCompat.registerReceiver(this, levelReceiver, IntentFilter(br.com.betinhos.atalocal.audio.RecordingService.ACTION_LEVEL), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, stoppedReceiver, IntentFilter(br.com.betinhos.atalocal.audio.RecordingService.ACTION_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, recordingErrorReceiver, IntentFilter(br.com.betinhos.atalocal.audio.RecordingService.ACTION_ERROR), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch(Dispatchers.IO) {
            val days = getSharedPreferences("atalocal.settings", MODE_PRIVATE).getInt("retention_days", 30)
            cleanupExpiredAudio(filesDir.resolve("meetings"), meetingDao.listAll(), System.currentTimeMillis(), RetentionPolicy(days))
            val nowDays = System.currentTimeMillis() / 86_400_000L
            val transcriptDays = getSharedPreferences("atalocal.settings", MODE_PRIVATE).getInt("retention_transcripts_days", 180)
            val artifactDays = getSharedPreferences("atalocal.settings", MODE_PRIVATE).getInt("retention_minutes_days", 365)
            cleanupExpiredDerivedData(database, meetingDao.listAll(), nowDays, RetentionPolicy(transcriptDays), RetentionPolicy(artifactDays))
            PipelineRecovery.recover(this@MainActivity, database)
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AtaLocalTheme {
                var selectedMeeting by rememberSaveable { mutableStateOf<String?>(null) }
                var showDiagnostics by rememberSaveable { mutableStateOf(false) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val detailMeetingId = selectedMeeting ?: openMeetingAfterStop
                if (recordingUiMeetingId != null) RecordingScreen(recordingUiStartedAt, microphoneLevel, ::togglePause, ::stopRecording)
                else if (detailMeetingId != null) MeetingDetailScreen(database, detailMeetingId, onBack = { selectedMeeting = null; openMeetingAfterStop = null })
                else if (showDiagnostics) DiagnosticsScreen(database.modelInstallDao(), meetingDao, onBack = { showDiagnostics = false })
                else if (showSettings) SettingsScreen(database.modelInstallDao(), onBack = { showSettings = false })
                else HomeScreen(database, meetingDao, database.modelInstallDao(), onStartRecording = ::requestRecording, onStopRecording = ::stopRecording, onTogglePause = ::togglePause, onOpenMeeting = { selectedMeeting = it }, onOpenDiagnostics = { showDiagnostics = true }, onOpenSettings = { showSettings = true })
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(levelReceiver)
        unregisterReceiver(stoppedReceiver)
        unregisterReceiver(recordingErrorReceiver)
        super.onDestroy()
    }

    private fun requestRecording(meetingId: String) {
        activeMeetingId = meetingId
        recordingStartedAt = System.currentTimeMillis()
        recordingUiMeetingId = meetingId
        recordingUiStartedAt = recordingStartedAt
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingService()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingService() {
        activeMeetingId?.let { id -> lifecycleScope.launch { meetingDao.updateStatusClearingError(id, br.com.betinhos.atalocal.domain.MeetingStatus.RECORDING) } }
        val intent = Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java)
            .putExtra(
                br.com.betinhos.atalocal.audio.RecordingService.EXTRA_DIRECTORY,
                filesDir.resolve("meetings").resolve(activeMeetingId ?: "unknown").resolve("segments").path
            )
            .putExtra(br.com.betinhos.atalocal.audio.RecordingService.EXTRA_MEETING_ID, activeMeetingId)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecording() {
        val id = activeMeetingId ?: return
        startService(Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java).apply {
            action = br.com.betinhos.atalocal.audio.RecordingService.ACTION_STOP
            putExtra(br.com.betinhos.atalocal.audio.RecordingService.EXTRA_MEETING_ID, id)
        })
        val duration = ((System.currentTimeMillis() - recordingStartedAt) / 1000).coerceAtLeast(0)
        lifecycleScope.launch {
            meetingDao.updateStatus(id, br.com.betinhos.atalocal.domain.MeetingStatus.RECORDED, duration)
        }
        activeMeetingId = null
        recordingUiMeetingId = null
        microphoneLevel = 0f
        openMeetingAfterStop = id
    }

    private fun togglePause() {
        startService(Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java).apply {
            action = br.com.betinhos.atalocal.audio.RecordingService.ACTION_TOGGLE_PAUSE
        })
    }
}

@Composable
private fun AtaLocalTheme(content: @Composable () -> Unit) {
    val dark = darkColorScheme(
        primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF062E69),
        secondary = Color(0xFF8DE1D4), onSecondary = Color(0xFF003731),
        background = Color(0xFF0B1018), onBackground = Color(0xFFE6EAF2),
        surface = Color(0xFF121A26), onSurface = Color(0xFFE6EAF2),
        surfaceVariant = Color(0xFF202B3A), onSurfaceVariant = Color(0xFFBEC8D8),
        error = Color(0xFFFFB4AB)
    )
    MaterialTheme(colorScheme = dark, typography = androidx.compose.material3.Typography(), content = content)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(database: AtaLocalDatabase, dao: MeetingDao, modelDao: ModelInstallDao, onStartRecording: (String) -> Unit, onStopRecording: () -> Unit, onTogglePause: () -> Unit, onOpenMeeting: (String) -> Unit, onOpenDiagnostics: () -> Unit, onOpenSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val availableStorage = remember { StatFs(context.filesDir.path).availableBytes }
    val meetings by dao.observeAll().collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    val installedModels by modelDao.observeAll().collectAsState(initial = emptyList())
    val hasWhisper = installedModels.any { it.kind == "whisper" && it.status == "INSTALLED" && java.io.File(it.filePath).isFile }
    val hasLlm = installedModels.any { it.kind == "llm" && it.status == "INSTALLED" && java.io.File(it.filePath).isFile }

    if (showModels) {
        ModelsScreen(modelDao) { showModels = false }
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = { Column { Text("AtaLocal", style = MaterialTheme.typography.titleLarge); Text("Seu espaço de reuniões", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    ) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 })) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tudo importante,\nsem sair do aparelho.", style = MaterialTheme.typography.headlineLarge)
                    Text("Grave com privacidade. Transforme conversas em atas claras e acionáveis.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
            }
            item {
                Button(
                    onClick = { dialogOpen = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("＋  Nova reunião", style = MaterialTheme.typography.titleMedium) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showModels = true }) { Text("Modelos") }
                    TextButton(onClick = onOpenDiagnostics) { Text("Diagnóstico") }
                    TextButton(onClick = onOpenSettings) { Text("Configurações") }
                }
                Text("${formatBytes(availableStorage)} disponíveis neste aparelho", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!hasWhisper || !hasLlm) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Prepare o processamento local", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !hasWhisper && !hasLlm -> "Instale um modelo Whisper para transcrever e um modelo de ata para gerar o resumo."
                                !hasWhisper -> "Instale um modelo Whisper para que as reuniões sejam transcritas."
                                else -> "Instale um modelo de ata para transformar a transcrição em uma ata factual."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showModels = true }) { Text("Abrir modelos") }
                    }
                }
            }
            items(meetings, key = { it.id }) { meeting ->
                val job by database.processingJobDao().observe(meeting.id).collectAsState(initial = null)
                Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onOpenMeeting(meeting.id) }) { Text(meeting.title, style = MaterialTheme.typography.titleMedium) }
                        Text("${meeting.status.userLabel()} · ${meeting.durationSeconds}s")
                        if (job != null && meeting.status in setOf(br.com.betinhos.atalocal.domain.MeetingStatus.QUEUED, br.com.betinhos.atalocal.domain.MeetingStatus.TRANSCRIBING, br.com.betinhos.atalocal.domain.MeetingStatus.GENERATING)) {
                            if (job!!.progress > 0f) LinearProgressIndicator(progress = { job!!.progress }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(job!!.checkpoint?.let(::homeCheckpointLabel) ?: "Processando localmente…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        meeting.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (meeting.status == br.com.betinhos.atalocal.domain.MeetingStatus.RECORDING) {
                            TextButton(onClick = onTogglePause) { Text("Pausar / continuar") }
                            TextButton(onClick = onStopRecording) { Text("Parar gravação") }
                        }
                    }
                }
            }
            if (meetings.isEmpty()) item { Text("Sua primeira gravação ficará armazenada somente neste aparelho.") }
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("Nova reunião") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        label = { Text("Título") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it },
                        label = { Text("Observação (opcional)") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { br.com.betinhos.atalocal.domain.createMeeting(title, note) }
                        .onSuccess { meeting ->
                            scope.launch { dao.upsert(meeting) }
                            dialogOpen = false
                            title = ""
                            note = ""
                            onStartRecording(meeting.id)
                        }
                }, enabled = title.isNotBlank()) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("Cancelar") } }
        )
    }
}

private fun homeCheckpointLabel(checkpoint: String): String = when {
    checkpoint == "queued" -> "Aguardando processamento local…"
    checkpoint == "gerando-ata" -> "Gerando ata…"
    checkpoint.startsWith("processing-") -> "Transcrevendo segmento ${checkpoint.removePrefix("processing-")}…"
    checkpoint.startsWith("segment-") -> "Transcrição salva: segmento ${checkpoint.removePrefix("segment-")}"
    else -> checkpoint.replace('-', ' ')
}
