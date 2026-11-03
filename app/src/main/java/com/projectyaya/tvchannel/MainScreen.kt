package com.projectyaya.tvchannel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.view.View
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private const val PREVIEW_DEBOUNCE_MS = 700L
private const val ZAP_DEBOUNCE_MS = 300L
private const val BANNER_MS = 3000L

/**
 * Two-column home: preview + info on the left, text channel list on the right.
 * One PlayerView sits above both layouts and is only resized (preview slot <-> fullscreen),
 * so the same surface and the same ExoPlayer keep playing across the switch.
 * Fullscreen zaps through the section the channel was opened from (up = next, down = previous).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    channels: List<Channel>,
    controller: PlayerController,
    antenna: AntennaController,
    subtitles: SubtitleController,
    forYou: ForYou? = null,
    antennaPreview: Boolean = false,
    initialLast: LastWatched? = null,
) {
    val context = LocalContext.current
    val categories = remember(channels) { categoriesOf(channels) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val visible = remember(channels, category, query) { filterChannels(channels, category, query) }
    var lastWatched by remember { mutableStateOf(initialLast) }
    // "Lanjutkan" is hidden while a filter or search is active.
    val resume = remember(lastWatched, channels, category, query) {
        if (category == null && query.isBlank()) resolveResume(lastWatched, channels) else null
    }
    val sections = remember(visible, forYou, resume) { buildSections(visible, forYou, resume) }
    // A channel can sit in several sections (e.g. RCTI under Nasional and MNC), so rows are keyed by
    // "<section key>/<channel id>"; the preview still follows the channel id alone.
    val rowKeys = remember(sections) { sections.flatMap { s -> s.channels.map { rowKey(s, it) } }.toSet() }
    val firstKey = sections.firstOrNull()?.let { s -> s.channels.firstOrNull()?.let { rowKey(s, it) } }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val fullscreenFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var focusedId by remember { mutableStateOf<String?>(null) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var didFullscreen by remember { mutableStateOf(false) }
    // Preview experiment (antennaPreview): the TvView is attached to the slot only after the debounce has passed.
    var previewAttached by remember { mutableStateOf(false) }
    var pendingLast by remember { mutableStateOf<LastWatched?>(null) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuCursor by remember { mutableIntStateOf(0) }
    var slot by remember { mutableStateOf<Rect?>(null) }
    val focused = channels.firstOrNull { it.id == focusedId }

    // The preview slot shows either the ExoPlayer stream or the antenna TvView, depending on what is focused.
    val focusedAntenna = focused?.antenna
    val shownStatus = if (focusedAntenna != null) antenna.status else controller.status
    // The TvView is attached only while it is used (fullscreen, or the small slot if the preview is enabled):
    // left attached at any size it blanks the rest of the window on this TV.
    val antennaViewVisible = focusedAntenna != null && (fullscreen || previewAttached)

    // Subtitles are for internet streams only: never for the tuner, never for a tile.
    val streamFocused = focused != null && focused.antenna == null && focused.launch.isEmpty()

    fun exitFullscreen() {
        menuOpen = false
        controller.setFullscreen(false)
        antenna.setVolume(0f)
        if (antennaPreview) previewAttached = focusedAntenna != null else antenna.stop() // no preview: release the tuner at once
        pendingLast?.let { lastWatched = it }
        fullscreen = false
    }

    fun onOk(section: Section, channel: Channel) {
        // A tile opens another app (Live TV) instead of playing a stream.
        if (channel.launch.isNotEmpty()) {
            if (!launchLiveTv(context, channel.launch)) {
                Toast.makeText(context, "Aplikasi Live TV tidak ditemukan di TV ini.", Toast.LENGTH_LONG).show()
            }
            return
        }
        // Zapping runs through the section the channel is opened from; the "Lanjutkan" row uses the one it was left in.
        focusedKey = if (section.key == RESUME_KEY) {
            val own = "${lastWatched?.section}/${channel.id}"
            own.takeIf { it in rowKeys } ?: rowKeys.firstOrNull { !it.startsWith("$RESUME_KEY/") && it.endsWith("/${channel.id}") }
        } else rowKey(section, channel)
        focusedId = channel.id
        // An antenna channel goes fullscreen with sound, through the same TvView.
        if (channel.antenna != null) {
            antenna.ensure(channel.antenna)
            antenna.setVolume(1f)
            bannerTick++
            fullscreen = true
            return
        }
        // A failed channel is not worth a fullscreen: reload it in the preview instead.
        if (controller.loadedId == channel.id && controller.status is PlaybackStatus.Failed) {
            controller.start(channel)
            return
        }
        controller.ensure(channel)
        controller.setFullscreen(true)
        bannerTick++
        fullscreen = true
    }

    /** Fullscreen zapping: [step] channels away in the current section, wrapping around. */
    fun zap(step: Int) {
        val section = sections.firstOrNull { s -> s.channels.any { rowKey(s, it) == focusedKey } } ?: return
        val next = zapTarget(section.channels, focusedId ?: return, step) ?: return
        menuOpen = false
        focusedKey = rowKey(section, next)
        focusedId = next.id
        bannerTick++
    }

    // Debounced playback: a newer focus change cancels this effect, and select() drops the old load at once.
    // In fullscreen it is zapping (short debounce, sound on); otherwise it is the muted preview.
    // Only the target kind keeps its player: the stream player and the tuner never run together.
    LaunchedEffect(focused?.id) {
        previewAttached = false // detached the moment focus moves; re-attached below once it has settled
        val channel = focused ?: return@LaunchedEffect
        val debounce = if (fullscreen) ZAP_DEBOUNCE_MS else PREVIEW_DEBOUNCE_MS
        when {
            channel.launch.isNotEmpty() -> { // a tile has no stream: empty preview instead of a spinner
                controller.stop()
                antenna.stop()
            }
            channel.antenna != null -> {
                controller.stop()
                if (!antennaPreview && !fullscreen) { // fullscreen only: nothing is tuned until OK
                    antenna.stop()
                    return@LaunchedEffect
                }
                antenna.setVolume(if (fullscreen) 1f else 0f)
                antenna.select(channel.antenna) // the tuner is slow to switch, so the same debounce applies
                delay(debounce)
                if (!fullscreen) previewAttached = true // exactly the preview slot: the layer box has its size
                antenna.ensure(channel.antenna)
            }
            else -> {
                antenna.stop()
                controller.select(channel)
                controller.setFullscreen(fullscreen)
                delay(debounce)
                controller.ensure(channel)
            }
        }
    }

    // Remember what is being watched once zapping settles; "Lanjutkan" shows it after leaving fullscreen.
    LaunchedEffect(focusedId, fullscreen) {
        val channel = focused?.takeIf { fullscreen && it.launch.isEmpty() } ?: return@LaunchedEffect
        delay(ZAP_DEBOUNCE_MS)
        val last = LastWatched(channel.id, channel.name, focusedKey.orEmpty().removeSuffix("/${channel.id}"))
        pendingLast = last
        saveLastWatched(context, last)
    }

    // Subtitles run only in fullscreen on an internet stream; anything else frees the translators and the cache.
    LaunchedEffect(fullscreen, focused?.id) {
        if (fullscreen && streamFocused && focused != null) subtitles.activate(focused) else subtitles.deactivate()
    }

    // The banner (number, name, group, kind) shows on every zap and on OK, then fades after a few seconds.
    LaunchedEffect(bannerTick) {
        if (bannerTick > 0) {
            bannerVisible = true
            delay(BANNER_MS)
            bannerVisible = false
        }
    }

    // Fullscreen takes D-pad focus; on exit focus returns to the last watched channel if the filter still shows
    // it, else the first visible one (also the startup case). That row may have scrolled out of the lazy list.
    LaunchedEffect(fullscreen) {
        if (fullscreen) didFullscreen = true
        else if (didFullscreen) {
            val index = (focusedKey?.takeIf { it in rowKeys } ?: firstKey)?.let { listIndexOf(sections, it) } ?: -1
            if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                listState.scrollToItem((index - 1).coerceAtLeast(0)) // keep the section header above it in view
            }
        }
        repeat(2) { withFrameNanos { } } // let lazy items compose before asking for focus
        runCatching {
            if (fullscreen) {
                fullscreenFocus.requestFocus()
            } else {
                val target = focusedKey?.takeIf { it in rowKeys } ?: firstKey
                target?.let { requesters[it] }?.requestFocus()
            }
        }
    }

    /** Hands D-pad focus to the list: the last focused row if the filter still shows it, else the first. */
    fun focusList() {
        runCatching { (focusedKey?.takeIf { it in rowKeys } ?: firstKey)?.let { requesters[it] }?.requestFocus() }
    }

    BackHandler(enabled = fullscreen) { if (menuOpen) menuOpen = false else exitFullscreen() }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.58f).fillMaxHeight().padding(start = 48.dp, end = 24.dp, top = 27.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                        .onGloballyPositioned { slot = it.boundsInRoot() },
                )
                Spacer(Modifier.height(16.dp))
                focused?.let { ChannelInfo(it) }
            }
            Column(Modifier.weight(0.42f).fillMaxHeight().padding(start = 24.dp, end = 48.dp, top = 27.dp)) {
                // Disabled while fullscreen so D-pad keys cannot move focus under the video.
                SearchField(query, onChange = { query = it }, enabled = !fullscreen, onDone = ::focusList)
                CategoryTabs(categories, selected = category, onSelect = { category = it }, enabled = !fullscreen)
                if (sections.isEmpty()) {
                    Text(
                        "Tidak ada channel yang cocok.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ChannelList(
                        sections, controller, listState, enabled = !fullscreen, requesters,
                        onFocus = { key, channel ->
                            focusedKey = key
                            focusedId = channel.id
                        },
                        onOk = ::onOk,
                    )
                }
            }
        }

        val density = LocalDensity.current
        val rect = slot
        val layerModifier = when {
            fullscreen -> Modifier.fillMaxSize()
            rect != null -> with(density) {
                Modifier.offset(rect.left.toDp(), rect.top.toDp()).size(rect.width.toDp(), rect.height.toDp())
            }
            else -> Modifier.size(0.dp)
        }
        Box(
            layerModifier.background(Color.Black)
                .then(
                    if (fullscreen) {
                        Modifier.onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            if (menuOpen) { // the subtitle menu owns up/down/OK/left; Back falls through to the BackHandler
                                val count = SubtitleMode.entries.size
                                when (e.key) {
                                    Key.DirectionUp -> menuCursor = (menuCursor + count - 1) % count
                                    Key.DirectionDown -> menuCursor = (menuCursor + 1) % count
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        subtitles.setMode(SubtitleMode.entries[menuCursor])
                                        menuOpen = false
                                    }
                                    Key.DirectionLeft -> menuOpen = false
                                    Key.ChannelUp -> zap(+1)
                                    Key.ChannelDown -> zap(-1)
                                    else -> return@onKeyEvent false
                                }
                                return@onKeyEvent true
                            }
                            when (e.key) {
                                Key.DirectionRight -> {
                                    if (!streamFocused) return@onKeyEvent false
                                    menuCursor = subtitles.mode.ordinal
                                    menuOpen = true
                                }
                                Key.DirectionUp, Key.ChannelUp -> zap(+1)
                                Key.DirectionDown, Key.ChannelDown -> zap(-1)
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    // OK: show the banner again; on a failed stream it also reloads it.
                                    if (focused?.antenna == null && controller.status is PlaybackStatus.Failed) controller.retry()
                                    bannerTick++
                                }
                                else -> return@onKeyEvent false
                            }
                            true
                        }.focusRequester(fullscreenFocus).focusable()
                    } else Modifier,
                ),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        isFocusable = false // D-pad focus stays in the Compose list
                        subtitleView?.visibility = View.GONE // subtitles are drawn by SubtitleOverlay
                        player = controller.player
                    }
                },
            )
            // Antenna video: same slot, above the player surface; in the hierarchy only while it is shown.
            if (antennaViewVisible) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { antenna.view })
            }
            if (focusedAntenna != null && !antennaViewVisible && !antennaPreview) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tekan OK untuk menonton siaran antena", textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
            }
            StatusOverlay(status = shownStatus, fullscreen = fullscreen)
            if (fullscreen && streamFocused) {
                SubtitleOverlay(subtitles.text, subtitles.status)
                if (menuOpen) SubtitleMenu(subtitles.mode, menuCursor, subtitles.menuStatus)
            }
            if (fullscreen && bannerVisible && focused != null) {
                val section = sections.firstOrNull { s -> s.channels.any { rowKey(s, it) == focusedKey } }
                Box(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 27.dp)) {
                    ChannelBanner(
                        number = section?.let { zapPosition(it.channels, focused.id) } ?: 0,
                        channel = focused,
                        group = section?.title.orEmpty(),
                    )
                }
            }
        }
    }
}

/** Index of the row [key] in the flat LazyColumn (one header item per section, then its channels). */
private fun listIndexOf(sections: List<Section>, key: String): Int {
    var i = 0
    for (s in sections) {
        i++ // header
        for (c in s.channels) {
            if (rowKey(s, c) == key) return i
            i++
        }
    }
    return -1
}
