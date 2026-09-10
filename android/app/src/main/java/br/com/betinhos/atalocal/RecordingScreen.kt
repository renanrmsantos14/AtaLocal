package br.com.betinhos.atalocal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import br.com.betinhos.atalocal.audio.recordingElapsedSeconds

@Composable
fun RecordingScreen(startedAt: Long, paused: Boolean, pauseStartedAt: Long, pausedDurationMs: Long, level: Float, onTogglePause: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val elapsed = recordingElapsedSeconds(startedAt, now, paused, pauseStartedAt, pausedDurationMs)
    val minutes = elapsed / 60
    val seconds = elapsed % 60
    val animatedLevel by animateFloatAsState(level.coerceIn(0f, 1f), animationSpec = spring(), label = "microphone-level")
    val statusColor by animateColorAsState(if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, label = "recording-status-color")
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (paused) "PAUSADO" else "GRAVANDO", style = MaterialTheme.typography.labelLarge, color = statusColor, letterSpacing = 1.8.sp)
                Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge)
                Text(if (paused) "A gravação está em pausa" else "O áudio fica somente neste aparelho", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("NÍVEL DO MICROFONE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
                LinearProgressIndicator(progress = { animatedLevel }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                Text(if (paused) "Microfone pausado" else if (animatedLevel < 0.02f) "Aguardando áudio" else "Áudio detectado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTogglePause, modifier = Modifier.weight(1f)) { Text(if (paused) "Continuar" else "Pausar") }
            Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Finalizar") }
        }
    }
}
