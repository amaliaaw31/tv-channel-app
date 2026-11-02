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
import androidx.compose.ui.platform.LocalDensity
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
fun MainScreen(channels: List<Channel>, controller: PlayerController) {
    val categories = remember(channels) { categoriesOf(channels) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val visible = remember(channels, category, query) { filterChannels(channels, category, query) }
    val groups = remember(visible) { groupByCountry(visible) }
    val firstId = groups.firstOrNull()?.channels?.firstOrNull()?.id
    val requesters = remember { HashMap<String, FocusRequester>() }
    val fullscreenFocus = remember { FocusRequester() }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var slot by remember { mutableStateOf<Rect?>(null) }
    val focused = channels.firstOrNull { it.id == focusedId }

    fun exitFullscreen() {
        controller.setFullscreen(false)
        fullscreen = false
    }

    fun onOk(channel: Channel) {
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
        controller.select(channel)
        delay(PREVIEW_DEBOUNCE_MS)
        controller.ensure(channel)
    }

    // Fullscreen takes D-pad focus; on exit focus returns to the last focused channel if the filter
    // still shows it, else the first visible one (also the startup case).
    LaunchedEffect(fullscreen) {
        repeat(2) { withFrameNanos { } } // let lazy items compose before asking for focus
        runCatching {
            if (fullscreen) {
                fullscreenFocus.requestFocus()
            } else {
                val target = focusedId?.takeIf { id -> visible.any { it.id == id } } ?: firstId
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
                if (groups.isEmpty()) {
                    Text(
                        "Tidak ada channel yang cocok.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ChannelList(groups, controller, enabled = !fullscreen, requesters, onFocus = { focusedId = it.id }, onOk = ::onOk)
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
            StatusOverlay(
                status = controller.status,
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
    groups: List<CountryGroup>,
    controller: PlayerController,
    enabled: Boolean,
    requesters: MutableMap<String, FocusRequester>,
    onFocus: (Channel) -> Unit,
    onOk: (Channel) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 27.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.code}") {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(group.channels, key = { it.id }) { channel ->
                // The highlight turns yellow/red when this channel is the loaded one and has failed.
                val failed = controller.status.takeIf { controller.loadedId == channel.id } as? PlaybackStatus.Failed
                ChannelRow(
                    channel,
                    enabled = enabled,
                    tint = failed?.let { describeError(it.errorCode, it.httpStatus).severity.color() }
                        ?: MaterialTheme.colorScheme.primary,
                    requester = requesters.getOrPut(channel.id) { FocusRequester() },
                    onFocus = { onFocus(channel) },
                    onOk = { onOk(channel) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelInfo(channel: Channel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(channel.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${countryName(channel.country)} · ${channel.category}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
        Text(
            channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
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
            PlaybackStatus.Playing -> Unit
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
