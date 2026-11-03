package com.projectyaya.tvchannel

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private const val CONTROLS_MS = 3000L
private const val ROTATION_RELEASE_MS = 1500L

/**
 * Touch UI for phones (the same APK as the TV): the player on top with the list below; turning the phone to landscape
 * (or the fullscreen button) makes the player fill the screen. Internet streams only: a phone has no tuner, so there
 * is no antenna tile or antenna channel. Playback, errors, "Lanjutkan" and subtitles are the same code as on the TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhoneScreen(
    channels: List<Channel>,
    controller: PlayerController,
    subtitles: SubtitleController,
    forYou: ForYou?,
    initialLast: LastWatched?,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var lastWatched by remember { mutableStateOf(initialLast) }
    var current by remember { mutableStateOf<Channel?>(null) }
    var currentSection by remember { mutableStateOf<String?>(null) } // key of the section it was opened from (prev/next stay in it)
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    val categories = remember(channels) { categoriesOf(channels) }
    val visible = remember(channels, category, query) { filterChannels(channels, category, query) }
    val resume = remember(lastWatched, channels, category, query) {
        if (category == null && query.isBlank()) resolveResume(lastWatched, channels) else null
    }
    val sections = remember(visible, forYou, resume) { buildSections(visible, forYou, resume) }
    val fullscreen = landscape && current != null

    fun play(section: Section, channel: Channel) {
        current = channel
        currentSection = if (section.key == RESUME_KEY) lastWatched?.section else section.key
        if (controller.loadedId == channel.id && controller.status is PlaybackStatus.Failed) controller.start(channel) else controller.ensure(channel)
        controller.setFullscreen(true) // sound on, and a dropped stream reconnects once before showing an error
        controlsTick++
    }

    /** Previous/next channel of the section the current one was opened from (wraps around, like the TV zapping). */
    fun step(delta: Int) {
        val now = current ?: return
        val list = sections.firstOrNull { it.key == currentSection }?.channels
            ?: sections.firstOrNull { it.key != RESUME_KEY && it.channels.any { c -> c.id == now.id } }?.channels
            ?: return
        val next = zapTarget(list, now.id, delta) ?: return
        current = next
        controller.select(next)
        controller.ensure(next)
        controlsTick++
    }

    fun exitFullscreen() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // The orientation request is released again once the rotation is done, so the sensor (auto-rotate) rules again.
    LaunchedEffect(landscape) {
        delay(ROTATION_RELEASE_MS)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    // System bars hide in fullscreen (swipe from the edge shows them briefly).
    LaunchedEffect(fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Subtitles follow the playing channel, inline or fullscreen.
    LaunchedEffect(current?.id) {
        current?.let { subtitles.activate(it) } ?: subtitles.deactivate()
    }

    // Remember what is being watched once it has played for a moment ("Lanjutkan").
    LaunchedEffect(current?.id) {
        val channel = current ?: return@LaunchedEffect
        delay(1000)
        val last = LastWatched(channel.id, channel.name, currentSection.orEmpty())
        saveLastWatched(context, last)
        lastWatched = last
    }

    // Controls: always shown inline; in fullscreen they fade a few seconds after the last touch or channel change.
    LaunchedEffect(controlsTick, fullscreen) {
        controlsVisible = true
        if (fullscreen) {
            delay(CONTROLS_MS)
            controlsVisible = false
        }
    }

    BackHandler(enabled = menuOpen || fullscreen) { if (menuOpen) menuOpen = false else exitFullscreen() }

    Box(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
            current?.let { channel ->
                PhonePlayer(
                    channel = channel,
                    controller = controller,
                    subtitles = subtitles,
                    fullscreen = fullscreen,
                    controlsVisible = controlsVisible,
                    modifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    onToggleControls = { if (fullscreen) controlsVisible = !controlsVisible else Unit },
                    onFullscreen = {
                        if (fullscreen) exitFullscreen() else activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onSubtitles = { menuOpen = true },
                    onPrevious = { step(-1) },
                    onNext = { step(+1) },
                )
            }
            if (!fullscreen) {
                PhoneSearch(query) { query = it }
                PhoneCategories(categories, category) { category = it }
                PhoneList(sections, current?.id, ::play)
            }
        }
        if (menuOpen) PhoneSubtitleMenu(subtitles) { menuOpen = false }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhonePlayer(
    channel: Channel,
    controller: PlayerController,
    subtitles: SubtitleController,
    fullscreen: Boolean,
    controlsVisible: Boolean,
    modifier: Modifier,
    onToggleControls: () -> Unit,
    onFullscreen: () -> Unit,
    onSubtitles: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier.background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (controller.status is PlaybackStatus.Failed) controller.retry() else onToggleControls()
        },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    subtitleView?.visibility = View.GONE // subtitles are drawn by SubtitleOverlay
                    player = controller.player
                }
            },
        )
        StatusOverlay(status = controller.status, fullscreen = fullscreen, touch = true)
        SubtitleOverlay(
            subtitles.text, subtitles.status,
            fontSize = if (fullscreen) 22.sp else 14.sp, sideInset = if (fullscreen) 48.dp else 12.dp, bottomInset = if (fullscreen) 32.dp else 8.dp,
        )
        if (controlsVisible) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fullscreen) GlyphButton(Glyph.Back, onFullscreen)
                Text(
                    channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Box(Modifier.size(48.dp).clickable(onClick = onSubtitles), contentAlignment = Alignment.Center) { CaptionBadge() }
                if (!fullscreen) GlyphButton(Glyph.Fullscreen, onFullscreen)
            }
            if (fullscreen) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) { GlyphButton(Glyph.Previous, onPrevious) }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) { GlyphButton(Glyph.Next, onNext) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneSearch(query: String, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Cari channel…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) Box(Modifier.size(44.dp).clickable { onChange("") }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Close) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneCategories(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf<String?>(null) + categories, key = { it ?: "all" }) { id ->
            Text(
                if (id == null) "Semua" else categoryLabel(id),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(if (id == selected) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneList(sections: List<Section>, playingId: String?, onPlay: (Section, Channel) -> Unit) {
    if (sections.isEmpty()) {
        Text("Tidak ada channel yang cocok.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        sections.forEach { section ->
            item(key = "header-${section.key}") {
                Text(
                    section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.channels, key = { rowKey(section, it) }) { channel ->
                val playing = channel.id == playingId
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (playing) accent.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { onPlay(section, channel) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    channel.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NOTE_COLOR, maxLines = 1) }
                    if (channel.hasCaption) CaptionBadge()
                }
            }
        }
    }
}

/** Subtitle mode picker (the same four choices as the TV menu), shown over the whole screen. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneSubtitleMenu(subtitles: SubtitleController, onClose: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(300.dp).background(Color(0xFF1C1B1F), RoundedCornerShape(16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Subtitle", style = MaterialTheme.typography.titleMedium, color = Color.White)
            SubtitleMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (mode == subtitles.mode) accent.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable {
                            subtitles.setMode(mode)
                            onClose()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(mode.label, color = Color.White)
                    if (mode == subtitles.mode) Text("aktif", style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
            Text(subtitles.menuStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCCCCCC), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private enum class Glyph { Fullscreen, Back, Previous, Next, Close }

@Composable
private fun GlyphButton(glyph: Glyph, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { GlyphIcon(glyph) }
}

/** Small icons drawn with strokes, so no icon library is needed. */
@Composable
private fun GlyphIcon(glyph: Glyph) {
    Canvas(Modifier.size(22.dp)) {
        val w = 2.dp.toPx()
        fun line(a: Offset, b: Offset) = drawLine(Color.White, a, b, w, StrokeCap.Round)
        val s = size.width
        when (glyph) {
            Glyph.Fullscreen -> {
                val c = s * 0.38f
                for ((x, y, dx, dy) in listOf(listOf(0f, 0f, 1f, 1f), listOf(s, 0f, -1f, 1f), listOf(0f, s, 1f, -1f), listOf(s, s, -1f, -1f))) {
                    line(Offset(x, y), Offset(x + dx * c, y))
                    line(Offset(x, y), Offset(x, y + dy * c))
                }
            }
            Glyph.Back -> {
                line(Offset(s * 0.9f, s * 0.5f), Offset(s * 0.1f, s * 0.5f))
                line(Offset(s * 0.1f, s * 0.5f), Offset(s * 0.5f, s * 0.1f))
                line(Offset(s * 0.1f, s * 0.5f), Offset(s * 0.5f, s * 0.9f))
            }
            Glyph.Previous -> arrowHead(s, left = true, w = w)
            Glyph.Next -> arrowHead(s, left = false, w = w)
            Glyph.Close -> {
                line(Offset(s * 0.15f, s * 0.15f), Offset(s * 0.85f, s * 0.85f))
                line(Offset(s * 0.85f, s * 0.15f), Offset(s * 0.15f, s * 0.85f))
            }
        }
    }
}

private fun DrawScope.arrowHead(s: Float, left: Boolean, w: Float) {
    val tip = if (left) s * 0.2f else s * 0.8f
    val back = if (left) s * 0.8f else s * 0.2f
    drawLine(Color.White, Offset(back, s * 0.1f), Offset(tip, s * 0.5f), w, StrokeCap.Round)
    drawLine(Color.White, Offset(tip, s * 0.5f), Offset(back, s * 0.9f), w, StrokeCap.Round)
}
