package com.projectyaya.tvchannel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore("settings")
private val MODE_KEY = stringPreferencesKey("subtitle_mode")

/**
 * Subtitles for internet streams in fullscreen: picks a text track and shows its lines, translated to Indonesian
 * when the plan says so (rules in [planSubtitles]). Not used for antenna channels. Only active between
 * [activate] and [deactivate]; the latter also frees the translators and the line cache.
 */
class SubtitleController(private val context: Context, private val player: Player, private val scope: CoroutineScope) {
    var mode by mutableStateOf(SubtitleMode.Auto)
        private set

    /** The line to draw, null for none. */
    var text by mutableStateOf<String?>(null)
        private set

    /** "Mengunduh paket bahasa…" while a model downloads. */
    var status by mutableStateOf<String?>(null)
        private set

    /** One line for the menu: what is happening or why nothing is (e.g. "Subtitle tidak tersedia"). */
    var menuStatus by mutableStateOf("")
        private set

    private val translator = LineTranslator { downloading -> status = if (downloading) "Mengunduh paket bahasa…" else null }
    private var channel: Channel? = null
    private var groups: List<Tracks.Group> = emptyList()
    private var tracks: List<TextTrack> = emptyList()
    private var plan: SubtitlePlan = SubtitlePlan.Unavailable
    private var planned = false
    private var history: List<String> = emptyList()
    private var lastRaw: String? = null
    private var job: Job? = null

    init {
        // No text is rendered until fullscreen asks for it.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (channel != null) refreshTracks(tracks)
            }

            override fun onCues(cueGroup: CueGroup) {
                if (channel != null) onLine(cueGroup)
            }
        })
        scope.launch {
            val saved = runCatching { context.settingsStore.data.first()[MODE_KEY] }.getOrNull()
            SubtitleMode.entries.firstOrNull { it.name == saved }?.let { if (channel == null) mode = it else setMode(it, save = false) }
        }
    }

    /** Fullscreen opened (or zapped to) this internet stream. */
    fun activate(channel: Channel) {
        if (this.channel?.id == channel.id) return
        reset()
        this.channel = channel
        // Already playing (OK from the preview): its tracks are known. Otherwise they arrive with onTracksChanged.
        if (player.currentMediaItem?.mediaId == channel.id) refreshTracks(player.currentTracks) else recompute(emptyList(), emptyList())
    }

    /** Fullscreen closed, the channel changed to an antenna one, or the app went away: free everything. */
    fun deactivate() {
        if (channel == null) return
        channel = null
        reset()
        menuStatus = ""
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

    /** The app went to the background: drop the models; they are loaded again on the next line. */
    fun trim() = translator.close()

    fun release() {
        job?.cancel()
        translator.release()
    }

    fun setMode(new: SubtitleMode, save: Boolean = true) {
        mode = new
        if (save) scope.launch { runCatching { context.settingsStore.edit { it[MODE_KEY] = new.name } } }
        if (channel != null) recompute(groups, tracks)
    }

    private fun reset() {
        job?.cancel()
        translator.close()
        text = null
        status = null
        history = emptyList()
        lastRaw = null
        groups = emptyList()
        tracks = emptyList()
        planned = false
    }

    private fun refreshTracks(all: Tracks) {
        val textGroups = all.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported && it.length > 0 }
        val list = textGroups.mapIndexed { i, g -> TextTrack(i, normalizeLang(g.getTrackFormat(0).language)) }
        // The selection we make triggers this callback again: only a different track list (or the first look) counts.
        if (planned && list == tracks) return
        recompute(textGroups, list)
    }

    private fun recompute(newGroups: List<Tracks.Group>, newTracks: List<TextTrack>) {
        val ch = channel ?: return
        groups = newGroups
        tracks = newTracks
        plan = planSubtitles(mode, ch.languages, tracks)
        planned = true
        job?.cancel()
        text = null
        lastRaw = null
        menuStatus = describePlan(mode, plan, ch.languages)
        val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        val show = plan as? SubtitlePlan.Show
        if (show != null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .addOverride(TrackSelectionOverride(groups[show.track.index].mediaTrackGroup, 0))
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }
        player.trackSelectionParameters = params.build()
    }

    private fun onLine(cueGroup: CueGroup) {
        val show = plan as? SubtitlePlan.Show ?: return
        val raw = cueGroup.cues.mapNotNull { it.text?.toString()?.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        if (raw.isEmpty()) {
            job?.cancel()
            text = null
            lastRaw = null
            return
        }
        if (raw == lastRaw) return
        lastRaw = raw
        history = pushLine(history, raw)
        if (!show.translate) {
            text = raw
            return
        }
        val window = identificationWindow(history)
        job?.cancel()
        job = scope.launch {
            val action = lineAction(raw, translator.identify(window), show.track.language)
            text = when (action) {
                LineAction.Original -> raw
                is LineAction.Translate -> action.from?.let { translator.translate(raw, it) } ?: raw
            }
        }
    }
}

