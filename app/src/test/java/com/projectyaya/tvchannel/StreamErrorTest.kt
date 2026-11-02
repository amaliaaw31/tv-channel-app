package com.projectyaya.tvchannel

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamErrorTest {
    private val other = PlaybackException.ERROR_CODE_UNSPECIFIED

    @Test
    fun httpStatusWinsOverErrorCode() {
        assertTrue("geo-block" in streamErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
        assertTrue("451" in streamErrorMessage(other, 451))
        assertTrue("tidak ditemukan" in streamErrorMessage(other, 404))
        assertTrue("HTTP 500" in streamErrorMessage(other, 500))
    }

    @Test
    fun networkAndFallbackMessages() {
        assertTrue("timeout" in streamErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null))
        assertTrue("terhubung" in streamErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
        assertTrue("kode error $other" in streamErrorMessage(other, null))
    }
}
