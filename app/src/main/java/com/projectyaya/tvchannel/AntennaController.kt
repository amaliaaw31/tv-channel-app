package com.projectyaya.tvchannel

import android.content.Context
import android.media.tv.TvContentRating
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val TAG = "Antenna"

/**
 * Plays antenna channels in a TvView, the counterpart of PlayerController for the TV's own tuner.
 * Starts muted (preview); fullscreen raises the volume. The tuner is shared with the Live TV app, so it is
 * released whenever the app goes to the background ([suspend]) and when another item takes the preview ([stop]).
 */
class AntennaController(context: Context) {
    val view = TvView(context).apply {
        setZOrderMediaOverlay(true) // above the ExoPlayer surface that shares the preview slot
        setStreamVolume(0f)
    }

    var status: PlaybackStatus by mutableStateOf(PlaybackStatus.Idle)
        private set

    private var wanted: AntennaRef? = null
    private var tuned: Long? = null

    init {
        view.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                Log.i(TAG, "video available")
                status = PlaybackStatus.Playing
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                Log.i(TAG, "video unavailable, reason=$reason")
                status = when (reason) {
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING, TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> PlaybackStatus.Loading
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY -> PlaybackStatus.Playing
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL -> PlaybackStatus.Notice("Sinyal antena lemah", Severity.Warning)
                    else -> PlaybackStatus.Notice("Siaran tidak tersedia", Severity.Warning)
                }
            }

            override fun onConnectionFailed(inputId: String) {
                Log.w(TAG, "tuner connection failed")
                status = PlaybackStatus.Notice("Tuner TV tidak bisa dipakai", Severity.Error)
            }

            override fun onDisconnected(inputId: String) {
                Log.w(TAG, "tuner disconnected")
                status = PlaybackStatus.Notice("Tuner TV terputus", Severity.Error)
            }

            override fun onContentBlocked(inputId: String, rating: TvContentRating) {
                status = PlaybackStatus.Notice("Siaran diblokir kontrol orang tua", Severity.Warning)
            }
        })
    }

    /** Focus moved to an antenna channel: free the tuner at once and show "Memuat…" until [ensure] tunes. */
    fun select(ref: AntennaRef) {
        wanted = ref
        if (tuned == ref.channelId) return
        view.reset()
        tuned = null
        status = PlaybackStatus.Loading
    }

    /** Tunes [ref] unless it is already tuned. */
    fun ensure(ref: AntennaRef) {
        wanted = ref
        if (tuned == ref.channelId) return
        tuned = ref.channelId
        status = PlaybackStatus.Loading
        Log.i(TAG, "tune channel ${ref.channelId}")
        view.tune(ref.inputId, TvContract.buildChannelUri(ref.channelId))
    }

    fun setVolume(volume: Float) = view.setStreamVolume(volume)

    /** Another kind of item took the preview: release the tuner for good. */
    fun stop() {
        wanted = null
        release()
    }

    /** App went to the background: release the tuner (the Live TV app may need it) but remember what was playing. */
    fun suspend() = release()

    /** App is back: tune what was playing before [suspend]. */
    fun resume() {
        wanted?.let { ensure(it) }
    }

    private fun release() {
        view.reset()
        tuned = null
        status = PlaybackStatus.Idle
    }
}
