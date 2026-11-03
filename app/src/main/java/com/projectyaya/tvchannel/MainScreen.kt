package com.projectyaya.tvchannel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.view.View
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
                SearchField(query, onChange = { query = it }, enabled = !fullscreen)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, enabled: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            // A D-pad focus alone does not always open the on-screen keyboard; OK does.
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) keyboard?.show()
                ok
            }
            .background(if (focused) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) accent else Color.White.copy(alpha = 0.2f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) Text("Cari channel…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}

/** Selection follows focus, like Android TV tab rows: moving onto a tab filters immediately. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryTabs(categories: List<String>, selected: String?, onSelect: (String?) -> Unit, enabled: Boolean) {
    val tabs = listOf<String?>(null) + categories
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tabs, key = { it ?: "all" }) { id ->
            Surface(
                onClick = { onSelect(id) },
                enabled = enabled,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) onSelect(id) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (id == selected) accent.copy(alpha = 0.35f) else Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = accent.copy(alpha = 0.55f),
                    focusedContentColor = Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, accent), shape = shape)),
            ) {
                Text(
                    if (id == null) "Semua" else categoryLabel(id),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelList(
    sections: List<Section>,
    controller: PlayerController,
    listState: LazyListState,
    enabled: Boolean,
    requesters: MutableMap<String, FocusRequester>,
    onFocus: (rowKey: String, channel: Channel) -> Unit,
    onOk: (Section, Channel) -> Unit,
) {
    // LazyColumn keeps the item that was on top in place, so a section that is inserted above it after the list
    // is already showing ("Untuk Anda" when the location arrives late, "Lanjutkan" when it is an antenna channel
    // that waits for the TV scan) would land off-screen. Bring them into view, unless the user has already
    // scrolled down past them.
    val pinned = sections.takeWhile { it.key == RESUME_KEY || it.key == "local" }
    val pinnedRows = pinned.sumOf { it.channels.size + 1 }
    LaunchedEffect(pinned.map { it.key }) {
        if (pinned.isEmpty()) return@LaunchedEffect
        repeat(2) { withFrameNanos { } } // let the list lay out with the new rows before reading where it is
        if (listState.firstVisibleItemIndex <= pinnedRows) listState.scrollToItem(0)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 27.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.key}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(section.channels, key = { rowKey(section, it) }) { channel ->
                // The highlight turns yellow/red when this channel is the loaded one and has failed.
                val failed = controller.status.takeIf { controller.loadedId == channel.id } as? PlaybackStatus.Failed
                ChannelRow(
                    channel,
                    enabled = enabled,
                    tint = failed?.let { describeError(it.errorCode, it.httpStatus).severity.color() }
                        ?: MaterialTheme.colorScheme.primary,
                    requester = requesters.getOrPut(rowKey(section, channel)) { FocusRequester() },
                    onFocus = { onFocus(rowKey(section, channel), channel) },
                    onOk = { onOk(section, channel) },
                )
            }
        }
    }
}

private fun rowKey(section: Section, channel: Channel) = "${section.key}/${channel.id}"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelInfo(channel: Channel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(channel.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (channel.antenna != null) {
            Text(
                "Siaran antena (tuner TV) · Live TV ${channel.note.orEmpty()}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (channel.launch.isEmpty()) {
            Text(
                "${countryName(channel.country)} · ${channel.category}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            channel.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = NOTE_COLOR) }
        } else {
            // A tile: say what OK does and how to come back.
            Text(
                "Tekan OK untuk membuka Live TV (siaran antena). Tekan Kembali di sana untuk kembali ke app ini.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Same yellow as the "warning" error color, so a note reads as "not verified" rather than "broken".
private val NOTE_COLOR = Color(0xFFFFD54F)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    enabled: Boolean,
    tint: Color,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onOk: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onOk,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = tint.copy(alpha = 0.25f),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, tint), shape = shape)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            // Yellow marks "not verified"; a tile's note is just a hint, so it stays neutral.
            val noteColor = if (channel.launch.isEmpty() && channel.antenna == null) NOTE_COLOR else MaterialTheme.colorScheme.onSurfaceVariant
            channel.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = noteColor, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusOverlay(status: PlaybackStatus, fullscreen: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (status) {
            PlaybackStatus.Loading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spinner(Color.White)
                Text("Memuat…")
            }
            is PlaybackStatus.Failed -> {
                val info = describeError(status.errorCode, status.httpStatus)
                // Text only, no buttons: in fullscreen up/down keep zapping and Back keeps leaving.
                PreviewError(info, if (fullscreen) "OK: coba lagi · atas/bawah: ganti channel · Kembali: daftar" else "Tekan OK untuk coba lagi")
            }
            is PlaybackStatus.Notice -> Text(
                status.title,
                color = status.severity.color(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            PlaybackStatus.Playing, PlaybackStatus.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PreviewError(info: ErrorInfo, hint: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        Text(info.title, style = MaterialTheme.typography.titleMedium, color = info.severity.color(), textAlign = TextAlign.Center)
        info.code?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

/** Fullscreen corner banner: position in the section, name, country/group, and an icon for antenna or internet. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelBanner(number: Int, channel: Channel, group: String) {
    val detail = if (channel.antenna != null) "Antena · $group · Live TV ${channel.note.orEmpty()}".trim() else "${countryName(channel.country)} · $group"
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(number.toString(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Column {
            Text(channel.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        KindIcon(antenna = channel.antenna != null)
    }
}

/** Antenna (rabbit ears) or internet (globe) glyph, drawn so no icon library is needed. */
@Composable
private fun KindIcon(antenna: Boolean) {
    val color = Color.White
    Canvas(Modifier.size(28.dp)) {
        val stroke = 2.5.dp.toPx()
        if (antenna) {
            val base = Offset(size.width / 2, size.height * 0.9f)
            drawLine(color, base, Offset(size.width * 0.2f, size.height * 0.1f), stroke, StrokeCap.Round)
            drawLine(color, base, Offset(size.width * 0.8f, size.height * 0.1f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.3f, size.height * 0.9f), Offset(size.width * 0.7f, size.height * 0.9f), stroke, StrokeCap.Round)
        } else {
            drawCircle(color, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
            drawOval(color, Offset(size.width * 0.3f, stroke), Size(size.width * 0.4f, size.height - 2 * stroke), style = Stroke(stroke))
            drawLine(color, Offset(stroke, size.height / 2), Offset(size.width - stroke, size.height / 2), stroke)
        }
    }
}

private fun Severity.color() = if (this == Severity.Warning) Color(0xFFFFD54F) else Color(0xFFFF6B6B)

@Composable
private fun Spinner(color: Color) {
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(Modifier.size(32.dp)) {
        drawArc(color, startAngle = angle, sweepAngle = 270f, useCenter = false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    }
}
