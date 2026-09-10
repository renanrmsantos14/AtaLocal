package br.com.betinhos.atalocal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RecordingScreen(startedAt: Long, level: Float, onTogglePause: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val elapsed = ((now - startedAt) / 1000).coerceAtLeast(0)
    val minutes = elapsed / 60
    val seconds = elapsed % 60
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Gravando", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(32.dp))
        Text("Sensibilidade do microfone", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
        Text(if (level < 0.02f) "Aguardando áudio" else "Áudio detectado")
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTogglePause) { Text("Pausar / continuar") }
            Button(onClick = onStop) { Text("Finalizar") }
        }
    }
}
