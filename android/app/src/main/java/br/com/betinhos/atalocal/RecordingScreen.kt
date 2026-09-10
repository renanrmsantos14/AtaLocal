package br.com.betinhos.atalocal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    val animatedLevel by animateFloatAsState(level.coerceIn(0f, 1f), animationSpec = spring(), label = "microphone-level")
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("GRAVANDO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(32.dp))
        Text("Sensibilidade do microfone", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { animatedLevel }, Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.secondary)
        Text(if (animatedLevel < 0.02f) "Aguardando áudio" else "Áudio detectado", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTogglePause) { Text("Pausar / continuar") }
            Button(onClick = onStop) { Text("Finalizar") }
        }
    }
}
