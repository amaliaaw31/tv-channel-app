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
    val groups = remember(channels) { groupByCountry(channels) }
    val firstId = groups.first().channels.first().id
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

    // Fullscreen takes D-pad focus; on exit focus returns to the last focused channel (first one at startup).
    LaunchedEffect(fullscreen) {
        repeat(2) { withFrameNanos { } } // let lazy items compose before asking for focus
        runCatching {
            if (fullscreen) fullscreenFocus.requestFocus() else requesters[focusedId ?: firstId]?.requestFocus()
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
            LazyColumn(
                Modifier.weight(0.42f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
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
                        ChannelRow(
                            channel,
                            tint = MaterialTheme.colorScheme.primary,
                            requester = requesters.getOrPut(channel.id) { FocusRequester() },
                            onFocus = { focusedId = channel.id },
                            onOk = { onOk(channel) },
                        )
                    }
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
            StatusOverlay(controller.status)
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
    tint: Color,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onOk: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onOk,
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
private fun StatusOverlay(status: PlaybackStatus) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (status) {
            PlaybackStatus.Loading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spinner(Color.White)
                Text("Memuat…")
            }
            is PlaybackStatus.Failed -> Text(
                streamErrorMessage(status.errorCode, status.httpStatus),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            PlaybackStatus.Playing -> Unit
        }
    }
}

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
