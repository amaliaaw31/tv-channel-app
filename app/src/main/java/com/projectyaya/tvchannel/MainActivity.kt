package com.projectyaya.tvchannel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Theme blue sampled from the launcher icon's TV frame highlight.
private val AccentBlue = Color(0xFF3CA6FB)

class MainActivity : ComponentActivity() {
    private var loaded by mutableStateOf<Result<List<Channel>>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate. Splash stays until channels.json is parsed (success or failure).
        installSplashScreen().setKeepOnScreenCondition { loaded == null }
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            loaded = withContext(Dispatchers.IO) { runCatching { loadChannels(applicationContext) } }
        }
        setContent { loaded?.let { App(it) } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun App(result: Result<List<Channel>>) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context) }

    // Release on leave; pause when the app goes to background (Home) so audio does not keep playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.pause()
                Lifecycle.Event.ON_START -> controller.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AccentBlue, onPrimary = Color(0xFF060B24))) {
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp)) {
            val channels = result.getOrNull()
            when {
                channels == null -> Centered("Gagal membaca channels.json: ${result.exceptionOrNull()?.message}")
                channels.isEmpty() -> Centered("Tidak ada channel yang valid di channels.json.")
                else -> MainScreen(channels, controller)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}
