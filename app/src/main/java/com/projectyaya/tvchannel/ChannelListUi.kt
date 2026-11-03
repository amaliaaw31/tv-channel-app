package com.projectyaya.tvchannel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChannelList(
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

internal fun rowKey(section: Section, channel: Channel) = "${section.key}/${channel.id}"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChannelInfo(channel: Channel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(channel.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (channel.antenna != null) {
            Text(
                "Siaran antena (tuner TV) · Live TV ${channel.note.orEmpty()}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (channel.launch.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${countryName(channel.country)} · ${channel.category}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (channel.hasCaption) CaptionBadge()
            }
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

/** Small "CC" tag for channels whose stream really carries captions (WebVTT or CEA-608/708, from the caption audit). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CaptionBadge() {
    Text(
        "CC",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier.border(1.5.dp, Color.White, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

// Same yellow as the "warning" error color, so a note reads as "not verified" rather than "broken".
internal val NOTE_COLOR = Color(0xFFFFD54F)

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
