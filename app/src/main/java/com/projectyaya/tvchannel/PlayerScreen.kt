package com.projectyaya.tvchannel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Fullscreen player. Back returns to the list; a failed stream shows a message instead of crashing. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(channel: Channel, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember(channel.id) { mutableStateOf<String?>(null) }

    val player = remember(channel.id) {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true) // http <-> https redirects are common on IPTV
            .setDefaultRequestProperties(channel.headers)
        // The factory's own userAgent would otherwise win over a User-Agent in the headers map.
        channel.headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.let { http.setUserAgent(it.value) }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            // Live stream fell behind its window: rejoin the live edge.
                            p.seekToDefaultPosition()
                            p.prepare()
                            return
                        }
                        val status = generateSequence<Throwable>(e) { it.cause }
                            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                            .firstOrNull()?.responseCode
                        error = streamErrorMessage(e.errorCode, status)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) error = null
                    }
                })
                p.setMediaItem(MediaItem.fromUri(channel.streamUrl))
                p.playWhenReady = true
                p.prepare()
            }
    }

    // Release on leave; pause when the app goes to background (Home) so audio does not keep playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // live TV: no on-screen controls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    this.player = player
                }
            },
        )
        error?.let {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(48.dp), contentAlignment = Alignment.Center) {
                Text("$it\n\nTekan Kembali untuk memilih channel lain.", textAlign = TextAlign.Center)
            }
        }
    }
}

/** Maps a playback failure to a user-facing message. [httpStatus] is null when the failure was not an HTTP status. */
fun streamErrorMessage(errorCode: Int, httpStatus: Int?): String = when {
    httpStatus == 403 || httpStatus == 451 ->
        "Akses ditolak (HTTP $httpStatus). Stream mungkin diblokir di wilayah Anda (geo-block) atau butuh header khusus."
    httpStatus == 404 || httpStatus == 410 ->
        "Stream tidak ditemukan (HTTP $httpStatus). URL mungkin sudah tidak aktif."
    httpStatus != null -> "Server menolak permintaan (HTTP $httpStatus)."
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Koneksi ke stream timeout. Server lambat atau tidak merespons."
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        "Tidak bisa terhubung ke stream. Periksa koneksi internet TV."
    errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
        "Koneksi http tidak diizinkan oleh konfigurasi jaringan."
    else -> "Stream gagal diputar (kode error $errorCode)."
}
