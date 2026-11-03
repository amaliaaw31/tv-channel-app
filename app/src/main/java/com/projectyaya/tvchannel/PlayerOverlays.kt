package com.projectyaya.tvchannel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun StatusOverlay(status: PlaybackStatus, fullscreen: Boolean, touch: Boolean = false) {
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
                PreviewError(
                    info,
                    when {
                        touch -> "Ketuk untuk coba lagi"
                        fullscreen -> "OK: coba lagi · atas/bawah: ganti channel · Kembali: daftar"
                        else -> "Tekan OK untuk coba lagi"
                    },
                )
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
internal fun ChannelBanner(number: Int, channel: Channel, group: String) {
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

internal fun Severity.color() = if (this == Severity.Warning) Color(0xFFFFD54F) else Color(0xFFFF6B6B)

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
