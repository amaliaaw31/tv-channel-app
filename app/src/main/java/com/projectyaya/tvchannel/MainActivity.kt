package com.projectyaya.tvchannel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Theme blue sampled from the launcher icon's TV frame highlight.
private val AccentBlue = Color(0xFF3CA6FB)

class MainActivity : ComponentActivity() {
    private var loaded by mutableStateOf<Result<List<Channel>>?>(null)
    // Read before [loaded] is set, so "Lanjutkan" is in the first frame (a late insert would land off-screen).
    private var lastWatched by mutableStateOf<LastWatched?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate. Splash stays until channels.json is parsed (success or failure).
        installSplashScreen().setKeepOnScreenCondition { loaded == null }
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val last = async { readLastWatched(applicationContext) }
            val channels = withContext(Dispatchers.IO) { runCatching { loadChannels(applicationContext) } }
            lastWatched = last.await()
            loaded = channels
        }
        setContent { loaded?.let { App(it, lastWatched) } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun App(result: Result<List<Channel>>, lastWatched: LastWatched?) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context) }
    val antennaController = remember { AntennaController(context) }
    val subtitleScope = rememberCoroutineScope()
    val subtitleController = remember { SubtitleController(context, controller.player, subtitleScope) }

    // Two optional permissions, each asked once (a refusal is respected on later starts: no nagging on a TV):
    //  - coarse location -> region for the "Untuk Anda" section (docs/ui-spec.md)
    //  - TV listings     -> the antenna scan, so antenna channels can be tuned (docs/ui-spec.md)
    var forYou by remember { mutableStateOf<ForYou?>(null) }
    var scan by remember { mutableStateOf<List<ScanChannel>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val locationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
    val listingsPermission = "android.permission.READ_TV_LISTINGS"
    fun loadLocation() = scope.launch { forYou = findProvince(context)?.let(::forYouOf) }
    fun loadScan() = scope.launch { scan = withContext(Dispatchers.IO) { readScan(context) } }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[locationPermission] == true) loadLocation()
        if (granted[listingsPermission] == true) loadScan()
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        val toAsk = mutableListOf<String>()
        for ((permission, askedFlag, load) in listOf(
            Triple(locationPermission, "location_prompted", ::loadLocation),
            Triple(listingsPermission, "tv_listings_prompted", ::loadScan),
        )) {
            when {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> load()
                !prefs.getBoolean(askedFlag, false) -> {
                    prefs.edit().putBoolean(askedFlag, true).apply()
                    toAsk += permission
                }
            }
        }
        if (toAsk.isNotEmpty()) askPermissions.launch(toAsk.toTypedArray())
    }

    // Release on leave. Going to the background (Home, or Live TV from the tile) pauses the stream player and
    // frees the tuner so audio does not keep playing and Live TV can use the tuner.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, antennaController, subtitleController, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    controller.pause()
                    antennaController.suspend()
                    subtitleController.trim()
                }
                Lifecycle.Event.ON_START -> {
                    controller.resume()
                    antennaController.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            subtitleController.release()
            controller.release()
            antennaController.stop()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AccentBlue, onPrimary = Color(0xFF060B24))) {
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp)) {
            val channels = result.getOrNull()
            when {
                channels == null -> Centered("Gagal membaca channels.json: ${result.exceptionOrNull()?.message}")
                channels.isEmpty() -> Centered("Tidak ada channel yang valid di channels.json.")
                else -> {
                    // The antenna tile and the names of the antenna channels come from assets/antenna.json, separate
                    // from the stream list; the channel ids come from the TV's scan, read at runtime.
                    val tile = remember { loadAntennaTile(context) }
                    val wanted = remember { loadWantedChannels(context) }
                    val antennaPreview = remember { loadAntennaPreview(context) }
                    val antennaItems = remember(scan, wanted) { antennaChannels(scan, wanted) }
                    val all = remember(channels, tile, antennaItems) { channels + listOfNotNull(tile) + antennaItems }
                    MainScreen(all, controller, antennaController, subtitleController, forYou, antennaPreview, lastWatched)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}
