package com.projectyaya.tvchannel

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.launch

/**
 * Debug only: plays a test stream with subtitles through the real SubtitleController, fullscreen, to check
 * the pipeline on the TV (adb shell am start -n com.projectyaya.tvchannel/.SubtitleProbeActivity
 * --es lang eng --es mode Auto --ez menu true --es url https://...). Not part of the release app.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
class SubtitleProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
            ?: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
        val languages = intent.getStringExtra("lang")?.split(",").orEmpty()
        val mode = SubtitleMode.entries.firstOrNull { it.name == intent.getStringExtra("mode") } ?: SubtitleMode.Auto
        val channel = Channel("probe", "Probe", "US", "general", null, url, emptyMap(), languages = languages, caption = intent.getStringExtra("caption"))
        val player = PlayerController(this)
        val subtitles = SubtitleController(this, player.player, lifecycleScope)
        player.start(channel)
        player.setFullscreen(true)
        subtitles.setMode(mode, save = false)
        subtitles.activate(channel)
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000)
                val tracks = player.player.currentTracks.groups.joinToString(",") {
                    "t${it.type}:${it.getTrackFormat(0).sampleMimeType}:${it.getTrackFormat(0).language}${if (it.isSelected) "*" else ""}"
                }
                Log.i("SubtitleProbe", "state=${player.player.playbackState} err=${player.player.playerError?.errorCodeName} tracks=[$tracks] mode=$mode plan='${subtitles.menuStatus}' text='${subtitles.text?.replace("\n", " | ")}' status=${subtitles.status}")
            }
        }
        // --es sample "line one|line two|...": per-line check of detection + translation (as SubtitleController does it)
        intent.getStringExtra("sample")?.let { sample ->
            lifecycleScope.launch {
                val t = LineTranslator { Log.i("SubtitleProbe", "download=$it") }
                var history = emptyList<String>()
                for (raw in sample.split("|")) {
                    val line = sentenceCaseIfShouting(raw)
                    history = pushLine(history, line)
                    val detected = t.identify(identificationWindow(history))
                    val action = lineAction(line, detected, null)
                    val out = if (action is LineAction.Translate) action.from?.let { t.translate(line, it) } ?: line else line
                    Log.i("SubtitleProbe", "line='$line' detected=${detected.language}/${"%.2f".format(detected.confidence)} action=$action -> '$out'")
                }
                t.release()
            }
        }
        val menu = intent.getBooleanExtra("menu", false)
        setContent {
            Box(Modifier.fillMaxSize()) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> PlayerView(ctx).apply { useController = false; subtitleView?.visibility = android.view.View.GONE; this.player = player.player } })
                SubtitleOverlay(subtitles.text, subtitles.status)
                if (menu) SubtitleMenu(subtitles.mode, mutableIntStateOf(1).intValue, subtitles.menuStatus)
            }
        }
    }
}
