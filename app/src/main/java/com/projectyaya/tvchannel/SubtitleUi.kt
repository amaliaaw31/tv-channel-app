package com.projectyaya.tvchannel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Subtitle line at the bottom of the fullscreen video: large text on a semi-transparent plate, inset from the screen
 * edges (overscan). It takes no focus and no key events, so zapping and the channel banner (top left) are unaffected.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BoxScope.SubtitleOverlay(text: String?, status: String?) {
    if (text == null && status == null) return
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFD54F),
                modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        text?.let {
            Text(it, fontSize = 28.sp, lineHeight = 34.sp, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** Subtitle menu on the right of the fullscreen video, driven by the screen's key handler (up/down, OK, left). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BoxScope.SubtitleMenu(mode: SubtitleMode, cursor: Int, status: String) {
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 48.dp).width(320.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Subtitle", style = MaterialTheme.typography.titleMedium, color = Color.White)
        SubtitleMode.entries.forEachIndexed { i, m ->
            val tint = MaterialTheme.colorScheme.primary
            Row(
                Modifier.fillMaxWidth().background(if (i == cursor) tint.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(m.label, color = Color.White)
                if (m == mode) Text("aktif", style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCCCCCC), modifier = Modifier.padding(top = 4.dp))
    }
}
