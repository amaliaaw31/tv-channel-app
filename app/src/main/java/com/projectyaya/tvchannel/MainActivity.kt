package com.projectyaya.tvchannel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun App() {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf<Result<List<Channel>>?>(null) }
    // Saved by id so the selection survives configuration changes.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loaded = withContext(Dispatchers.IO) { runCatching { loadChannels(context) } }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp)) {
            val result = loaded
            val channels = result?.getOrNull()
            val selected = channels?.firstOrNull { it.id == selectedId }
            when {
                result == null -> Centered("Memuat channel…")
                channels == null -> Centered("Gagal membaca channels.json: ${result.exceptionOrNull()?.message}")
                selected != null -> PlayerScreen(selected, onBack = { selectedId = null })
                channels.isEmpty() -> Centered("Tidak ada channel yang valid di channels.json.")
                else -> ChannelListScreen(channels, onSelect = { selectedId = it.id })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}
