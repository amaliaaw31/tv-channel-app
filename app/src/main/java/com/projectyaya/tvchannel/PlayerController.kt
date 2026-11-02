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
                status = PlaybackStatus.Failed(e.errorCode, httpStatus)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> status = PlaybackStatus.Loading
                    Player.STATE_READY -> status = PlaybackStatus.Playing
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

    /** Loads [channel] unless it is already loaded. */
    fun ensure(channel: Channel) {
        if (channel.id != loadedId) start(channel)
    }

    /** (Re)loads [channel] from scratch. */
    fun start(channel: Channel) {
        http.setDefaultRequestProperties(channel.headers)
        // The factory's own userAgent would otherwise win over a User-Agent in the headers map.
        http.setUserAgent(channel.headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value)
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        loadedId = channel.id
        status = PlaybackStatus.Loading
    }

    fun setFullscreen(on: Boolean) {
        player.volume = if (on) 1f else 0f
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun release() = player.release()
}
