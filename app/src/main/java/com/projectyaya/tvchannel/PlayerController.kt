package com.projectyaya.tvchannel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

sealed interface PlaybackStatus {
    /** Nothing is loaded on purpose (the focused item is a tile that opens another app). */
    data object Idle : PlaybackStatus
    /** A plain message instead of video, e.g. a weak antenna signal. */
    data class Notice(val title: String, val severity: Severity) : PlaybackStatus
    data object Loading : PlaybackStatus
    data object Playing : PlaybackStatus
    data class Failed(val errorCode: Int, val httpStatus: Int?) : PlaybackStatus
}

/**
 * Owns the single ExoPlayer shared by the muted preview and the fullscreen view,
 * so entering fullscreen keeps the same stream without re-buffering.
 */
class PlayerController(context: Context) {
    private val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(10_000)
        .setAllowCrossProtocolRedirects(true) // http <-> https redirects are common on IPTV

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
        .build()

    var status: PlaybackStatus by mutableStateOf(PlaybackStatus.Loading)
        private set

    /** Channel whose stream is loaded in the player; null while a preview is still pending. */
    var loadedId: String? by mutableStateOf(null)
        private set

    private var fullscreen = false
    private var autoReconnects = 0

    init {
        player.volume = 0f // preview starts muted
        player.playWhenReady = true
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Live stream fell behind its window: rejoin the live edge.
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                val httpStatus = generateSequence<Throwable>(e) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.responseCode
                if (fullscreen && autoReconnects < 1) {
                    // Fullscreen dropped: silently reconnect once before telling the user.
                    autoReconnects++
                    retry(resetCounter = false)
                    return
                }
                status = PlaybackStatus.Failed(e.errorCode, httpStatus)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> status = PlaybackStatus.Loading
                    Player.STATE_READY -> {
                        autoReconnects = 0 // a working stream earns a fresh reconnect attempt
                        status = PlaybackStatus.Playing
                    }
                }
            }
        })
    }

    /** Focus moved to [channel]: drop the previous load right away, before the debounce expires. */
    fun select(channel: Channel) {
        if (channel.id == loadedId) return
        player.stop()
        loadedId = null
        status = PlaybackStatus.Loading
    }

    /** Stops playback and shows an empty preview. */
    fun stop() {
        player.stop()
        loadedId = null
        status = PlaybackStatus.Idle
    }

    /** Loads [channel] unless it is already loaded. */
    fun ensure(channel: Channel) {
        if (channel.id != loadedId) start(channel)
    }

    /** (Re)loads [channel] from scratch. */
    fun start(channel: Channel) {
        http.setDefaultRequestProperties(channel.headers)
        // The factory's own userAgent would otherwise win over a User-Agent in the headers map.
        http.setUserAgent(channel.headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value)
        player.setMediaItem(MediaItem.Builder().setUri(channel.streamUrl).setMediaId(channel.id).build())
        player.prepare()
        loadedId = channel.id
        autoReconnects = 0
        status = PlaybackStatus.Loading
    }

    /** Re-prepares the loaded stream at the live edge (user-triggered "Coba lagi" or the automatic reconnect). */
    fun retry(resetCounter: Boolean = true) {
        if (resetCounter) autoReconnects = 0
        status = PlaybackStatus.Loading
        player.seekToDefaultPosition()
        player.prepare()
    }

    fun setFullscreen(on: Boolean) {
        fullscreen = on
        autoReconnects = 0
        player.volume = if (on) 1f else 0f
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun release() = player.release()
}
