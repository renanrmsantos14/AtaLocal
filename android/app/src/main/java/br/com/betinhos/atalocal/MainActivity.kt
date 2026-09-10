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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.core.content.ContextCompat
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.MeetingDao
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, AtaLocalDatabase::class.java, "atalocal.db").build()
        setContent {
            AtaLocalTheme {
                HomeScreen(database.meetingDao(), onStartRecording = ::requestRecording, onStopRecording = ::stopRecording)
            }
        }
    }

    private fun requestRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingService()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java)
            .putExtra(
                br.com.betinhos.atalocal.audio.RecordingService.EXTRA_DIRECTORY,
                filesDir.resolve("segments").path
            )
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecording() {
        stopService(Intent(this, br.com.betinhos.atalocal.audio.RecordingService::class.java))
    }
}

@Composable
private fun AtaLocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun HomeScreen(dao: MeetingDao, onStartRecording: () -> Unit, onStopRecording: () -> Unit) {
    val meetings by dao.observeAll().collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

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
                            onStartRecording()
                        }
                }, enabled = title.isNotBlank()) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("Cancelar") } }
        )
    }
}
