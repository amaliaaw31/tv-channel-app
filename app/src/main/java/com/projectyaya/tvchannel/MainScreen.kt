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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private const val PREVIEW_DEBOUNCE_MS = 700L

/**
 * Two-column home: preview + info on the left, text channel list on the right.
 * One PlayerView sits above both layouts and is only resized (preview slot <-> fullscreen),
 * so the same surface and the same ExoPlayer keep playing across the switch.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(channels: List<Channel>, controller: PlayerController, antenna: AntennaController, forYou: ForYou? = null) {
    val context = LocalContext.current
    val categories = remember(channels) { categoriesOf(channels) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val visible = remember(channels, category, query) { filterChannels(channels, category, query) }
    val sections = remember(visible, forYou) { buildSections(visible, forYou) }
    // A channel can sit in several sections (e.g. RCTI under Nasional and MNC), so rows are keyed by
    // "<section key>/<channel id>"; the preview still follows the channel id alone.
    val rowKeys = remember(sections) { sections.flatMap { s -> s.channels.map { rowKey(s, it) } }.toSet() }
    val firstKey = sections.firstOrNull()?.let { s -> s.channels.firstOrNull()?.let { rowKey(s, it) } }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val fullscreenFocus = remember { FocusRequester() }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var slot by remember { mutableStateOf<Rect?>(null) }
    val focused = channels.firstOrNull { it.id == focusedId }

    // The preview slot shows either the ExoPlayer stream or the antenna TvView, depending on what is focused.
    val focusedAntenna = focused?.antenna
    val shownStatus = if (focusedAntenna != null) antenna.status else controller.status

    fun exitFullscreen() {
        controller.setFullscreen(false)
        antenna.setVolume(0f)
        fullscreen = false
    }

    fun onOk(channel: Channel) {
        // A tile opens another app (Live TV) instead of playing a stream.
        if (channel.launch.isNotEmpty()) {
            if (!launchLiveTv(context, channel.launch)) {
                Toast.makeText(context, "Aplikasi Live TV tidak ditemukan di TV ini.", Toast.LENGTH_LONG).show()
            }
            return
        }
        // An antenna channel goes fullscreen with sound, through the same TvView.
        if (channel.antenna != null) {
            antenna.ensure(channel.antenna)
            antenna.setVolume(1f)
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
        fullscreen = true
    }

    // Debounced preview: a newer focus change cancels this effect, and select() drops the old load at once.
    LaunchedEffect(focused?.id) {
        val channel = focused ?: return@LaunchedEffect
        when {
            channel.launch.isNotEmpty() -> { // a tile has no stream: empty preview instead of a spinner
                controller.stop()
                antenna.stop()
            }
            channel.antenna != null -> { // the tuner is slow to switch, so the same debounce applies
                controller.stop()
                antenna.select(channel.antenna)
                delay(PREVIEW_DEBOUNCE_MS)
                antenna.ensure(channel.antenna)
            }
            else -> {
                antenna.stop()
                controller.select(channel)
                delay(PREVIEW_DEBOUNCE_MS)
                controller.ensure(channel)
            }
        }
    }

    // Fullscreen takes D-pad focus; on exit focus returns to the last focused channel if the filter
    // still shows it, else the first visible one (also the startup case).
    LaunchedEffect(fullscreen) {
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

    BackHandler(enabled = fullscreen) { exitFullscreen() }

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
                        sections, controller, enabled = !fullscreen, requesters,
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
                .then(if (fullscreen) Modifier.focusRequester(fullscreenFocus).focusable() else Modifier),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        isFocusable = false // D-pad focus stays in the Compose list
                        player = controller.player
                    }
                },
            )
            // Antenna video: same slot, above the player surface; zero-sized (and reset) when not in use.
            AndroidView(
                modifier = if (focusedAntenna != null) Modifier.fillMaxSize() else Modifier.size(0.dp),
                factory = { antenna.view },
            )
            StatusOverlay(
                status = shownStatus,
                fullscreen = fullscreen,
                onRetry = controller::retry,
                onBack = ::exitFullscreen,
            )
        }
    }
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
    enabled: Boolean,
    requesters: MutableMap<String, FocusRequester>,
    onFocus: (rowKey: String, channel: Channel) -> Unit,
    onOk: (Channel) -> Unit,
) {
    // LazyColumn keeps the item that was on top in place, so a "Untuk Anda" section that is inserted above it
    // after the list is already showing (location arrives late) would land off-screen. Bring it into view,
    // unless the user has already scrolled down past it.
    val listState = rememberLazyListState()
    val hasLocal = sections.firstOrNull()?.key == "local"
    val localRows = if (hasLocal) sections[0].channels.size + 1 else 0
    LaunchedEffect(hasLocal) {
        if (hasLocal && listState.firstVisibleItemIndex <= localRows) listState.scrollToItem(0)
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
                    onOk = { onOk(channel) },
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
private fun StatusOverlay(status: PlaybackStatus, fullscreen: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
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
                if (fullscreen) DroppedBroadcast(info, onRetry, onBack) else PreviewError(info)
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
private fun PreviewError(info: ErrorInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        Text(info.title, style = MaterialTheme.typography.titleMedium, color = info.severity.color(), textAlign = TextAlign.Center)
        info.code?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Tekan OK untuk coba lagi", style = MaterialTheme.typography.bodySmall)
    }
}

/** Shown only after the automatic reconnect also failed. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DroppedBroadcast(info: ErrorInfo, onRetry: () -> Unit, onBack: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retryFocus.requestFocus() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(48.dp),
    ) {
        Text("Siaran terputus", style = MaterialTheme.typography.headlineMedium)
        Text(info.title, color = info.severity.color(), textAlign = TextAlign.Center)
        info.code?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) { Text("Coba lagi") }
            Button(onClick = onBack) { Text("Kembali") }
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
