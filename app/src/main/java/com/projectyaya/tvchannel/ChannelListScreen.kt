package com.projectyaya.tvchannel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.AsyncImagePainter
import java.util.Locale

private val CARD_WIDTH = 180.dp
private val SHAPE = RoundedCornerShape(12.dp)

/** One row per country; D-pad moves between cards (left/right) and rows (up/down). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListScreen(channels: List<Channel>, onSelect: (Channel) -> Unit) {
    val byCountry = remember(channels) { channels.groupBy { it.country }.toSortedMap() }
    val firstId = channels.first().id // shortcut: always focus the first card; restore last position if it feels bad
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstCard.requestFocus() }

    LazyColumn(
        // Padding keeps content inside the TV overscan-safe area.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        byCountry.forEach { (code, list) ->
            item(key = code) {
                Text(countryName(code), style = MaterialTheme.typography.titleLarge)
            }
            item(key = "row-$code") {
                // Vertical padding leaves room for the focused card's scale-up.
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(list, key = { it.id }) { ch ->
                        ChannelCard(
                            ch,
                            onClick = { onSelect(ch) },
                            modifier = if (ch.id == firstId) Modifier.focusRequester(firstCard) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.width(CARD_WIDTH),
        shape = CardDefaults.shape(SHAPE),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = SHAPE),
        ),
    ) {
        Logo(channel, Modifier.fillMaxWidth().height(100.dp))
        Column(Modifier.padding(10.dp)) {
            Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(channel.category, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shows the logo; falls back to the name's initial when absent, loading, or failed. */
@Composable
private fun Logo(channel: Channel, modifier: Modifier) {
    val initial = @Composable {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(channel.name.trimStart('[', ']', ' ').take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
        }
    }
    if (channel.logo == null) {
        initial()
        return
    }
    SubcomposeAsyncImage(
        model = channel.logo,
        contentDescription = channel.name,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    ) {
        val state by painter.state.collectAsState()
        if (state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent() else initial()
    }
}

private fun countryName(code: String): String {
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.forLanguageTag("id"))
    return if (name.isBlank() || name == code) code else "$name ($code)"
}
