package com.projectyaya.tvchannel

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescribeErrorTest {
    private val badStatus = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    private val unspecified = PlaybackException.ERROR_CODE_UNSPECIFIED

    private fun check(errorCode: Int, httpStatus: Int?, title: String, severity: Severity) {
        val info = describeError(errorCode, httpStatus)
        assertEquals(title, info.title)
        assertEquals(severity, info.severity)
    }

    @Test
    fun timeoutIsYellow() =
        check(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null, "Server tidak merespons", Severity.Warning)

    @Test
    fun regionBlockIsYellow() {
        check(badStatus, 403, "Dibatasi wilayah", Severity.Warning)
        check(badStatus, 451, "Dibatasi wilayah", Severity.Warning)
    }

    @Test
    fun notFoundIsRed() {
        check(badStatus, 404, "Channel tidak ditemukan", Severity.Error)
        check(badStatus, 410, "Channel tidak ditemukan", Severity.Error)
    }

    @Test
    fun noInternetIsRed() =
        check(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, "Tidak ada internet", Severity.Error)

    @Test
    fun parsingAndUnsupportedFormatsAreRed() {
        listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ).forEach { check(it, null, "Format tidak didukung", Severity.Error) }
    }

    @Test
    fun everythingElseShowsASmallCode() {
        val other = describeError(unspecified, null)
        assertEquals("Channel tidak bisa diputar", other.title)
        assertEquals("Kode $unspecified", other.code)
        assertTrue("HTTP 500" in describeError(badStatus, 500).code!!)
        assertNull(describeError(badStatus, 404).code)
    }
}
