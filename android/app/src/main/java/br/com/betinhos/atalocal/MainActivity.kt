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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
                    onClick = { /* Fase 2: solicitar microfone e criar reunião */ },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Nova reunião") }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nenhuma reunião ainda", style = MaterialTheme.typography.titleMedium)
                        Text("Sua primeira gravação ficará armazenada somente neste aparelho.")
                    }
                }
            }
        }
    }
}
