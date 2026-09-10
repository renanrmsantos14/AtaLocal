package br.com.betinhos.atalocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AtaLocalTheme { HomeScreen() } }
    }
}

@Composable
private fun AtaLocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun HomeScreen() {
    var dialogOpen by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var createdTitle by remember { mutableStateOf<String?>(null) }

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
                        Text(createdTitle ?: "Nenhuma reunião ainda", style = MaterialTheme.typography.titleMedium)
                        Text(if (createdTitle == null) "Sua primeira gravação ficará armazenada somente neste aparelho."
                        else "Reunião criada como rascunho. A gravação será adicionada na próxima etapa.")
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
                        .onSuccess { createdTitle = it.title; dialogOpen = false; title = ""; note = "" }
                }, enabled = title.isNotBlank()) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("Cancelar") } }
        )
    }
}
