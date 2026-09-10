package br.com.betinhos.atalocal

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.MeetingDao
import br.com.betinhos.atalocal.data.ModelInstallDao
import br.com.betinhos.atalocal.pipeline.PipelineScheduler
import br.com.betinhos.atalocal.models.ModelsScreen
import br.com.betinhos.atalocal.models.selectWhisperModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private var activeMeetingId: String? = null
    private var recordingStartedAt: Long = 0L
    private lateinit var meetingDao: MeetingDao
    private lateinit var modelInstallDao: ModelInstallDao
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, AtaLocalDatabase::class.java, "atalocal.db").build()
        meetingDao = database.meetingDao()
        modelInstallDao = database.modelInstallDao()
        setContent {
            AtaLocalTheme {
                HomeScreen(meetingDao, database.modelInstallDao(), onStartRecording = ::requestRecording, onStopRecording = ::stopRecording)
            }
        }
    }

    private fun requestRecording(meetingId: String) {
        activeMeetingId = meetingId
        recordingStartedAt = System.currentTimeMillis()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingService()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingService() {
        activeMeetingId?.let { id -> lifecycleScope.launch { meetingDao.updateStatus(id, br.com.betinhos.atalocal.domain.MeetingStatus.RECORDING) } }
        val intent = Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java)
            .putExtra(
                br.com.betinhos.atalocal.audio.RecordingService.EXTRA_DIRECTORY,
                filesDir.resolve("meetings").resolve(activeMeetingId ?: "unknown").resolve("segments").path
            )
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecording() {
        stopService(Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java))
        val id = activeMeetingId ?: return
        val duration = ((System.currentTimeMillis() - recordingStartedAt) / 1000).coerceAtLeast(0)
        lifecycleScope.launch {
            meetingDao.updateStatus(id, br.com.betinhos.atalocal.domain.MeetingStatus.RECORDED, duration)
        }
        lifecycleScope.launch {
            val modelPath = selectWhisperModel(modelInstallDao.observeAll().first())
            PipelineScheduler.enqueue(this@MainActivity, id, modelPath)
        }
        activeMeetingId = null
    }
}

@Composable
private fun AtaLocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(dao: MeetingDao, modelDao: ModelInstallDao, onStartRecording: (String) -> Unit, onStopRecording: () -> Unit) {
    val meetings by dao.observeAll().collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }

    if (showModels) {
        ModelsScreen(modelDao) { showModels = false }
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("AtaLocal") }) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reuniões locais", style = MaterialTheme.typography.headlineMedium)
                    Text("Grave, transcreva e gere atas sem enviar seus dados para a nuvem.")
                }
            }
            item {
                Button(
                    onClick = { dialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Nova reunião") }
                TextButton(onClick = { showModels = true }) { Text("Gerenciar modelos") }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(meetings.firstOrNull()?.title ?: "Nenhuma reunião ainda", style = MaterialTheme.typography.titleMedium)
                        Text(if (meetings.isEmpty()) "Sua primeira gravação ficará armazenada somente neste aparelho."
                        else "${meetings.size} reunião(ões) armazenada(s) neste aparelho.")
                        if (meetings.isNotEmpty()) {
                            TextButton(onClick = onStopRecording) { Text("Parar gravação") }
                        }
                    }
                }
            }
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
