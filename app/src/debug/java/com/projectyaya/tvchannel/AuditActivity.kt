package com.projectyaya.tvchannel

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

private const val TAG = "Audit"
private const val TIMEOUT_MS = 25_000L

/**
 * Debug-only. Plays every bundled channel once (muted, same player and error mapping as the app)
 * and appends one JSON line per channel to filesDir/audit.jsonl. Resumable: channels already in the
 * file are skipped, so a crash or restart continues where it stopped. filesDir/audit.done marks the end.
 * Usage and pruning: docs/sources.md.
 */
class AuditActivity : ComponentActivity() {
    private lateinit var controller: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller = PlayerController(this)
        val results = File(filesDir, "audit.jsonl")
        val done = File(filesDir, "audit.done").also { it.delete() }

        setContent {
            var progress by remember { mutableStateOf("Memuat daftar channel…") }
            LaunchedEffect(Unit) {
                val channels = withContext(Dispatchers.IO) { loadChannels(applicationContext) }
                val finished = withContext(Dispatchers.IO) {
                    if (results.exists()) results.readLines().filter { it.isNotBlank() }.map { JSONObject(it).getString("id") }.toSet()
                    else emptySet()
                }
                val pending = channels.filter { it.id !in finished }
                Log.i(TAG, "start: ${pending.size} to do, ${finished.size} already done, ${channels.size} total")
                for ((i, channel) in pending.withIndex()) {
                    progress = "Audit ${finished.size + i + 1}/${channels.size}\n${channel.name}"
                    val line = audit(controller, channel)
                    withContext(Dispatchers.IO) { results.appendText(line + "\n") }
                }
                done.writeText("ok")
                progress = "Audit selesai"
                Log.i(TAG, "DONE")
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.size(320.dp, 180.dp).align(Alignment.TopStart).padding(16.dp),
                    factory = { ctx -> PlayerView(ctx).apply { useController = false; player = controller.player } },
                )
                BasicText(progress, Modifier.align(Alignment.Center), style = TextStyle(color = Color.White, fontSize = 24.sp))
            }
        }
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}

/** Plays [channel] until it reaches a stable state or times out; returns the JSON line to record. */
private suspend fun audit(controller: PlayerController, channel: Channel): String {
    controller.select(channel) // stop the previous stream so its late callbacks cannot count for this one
    delay(150)
    val t0 = SystemClock.elapsedRealtime()
    controller.start(channel)
    val end = withTimeoutOrNull(TIMEOUT_MS) { snapshotFlow { controller.status }.first { it !is PlaybackStatus.Loading } }
    val json = JSONObject().put("id", channel.id).put("ms", SystemClock.elapsedRealtime() - t0)
    when (end) {
        PlaybackStatus.Playing -> json.put("result", "ok")
        is PlaybackStatus.Failed -> {
            json.put("result", "error").put("title", describeError(end.errorCode, end.httpStatus).title).put("errorCode", end.errorCode)
            end.httpStatus?.let { json.put("http", it) }
        }
        else -> json.put("result", "timeout")
    }
    return json.toString()
}
