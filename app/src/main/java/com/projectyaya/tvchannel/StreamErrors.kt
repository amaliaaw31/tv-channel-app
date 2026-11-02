package com.projectyaya.tvchannel

import androidx.media3.common.PlaybackException

/** Warning = may work later or elsewhere (yellow); Error = needs a different channel or setup (red). */
enum class Severity { Warning, Error }

/** [code] is a small technical hint, only set for failures without a dedicated message. */
data class ErrorInfo(val title: String, val severity: Severity, val code: String? = null)

/** Maps a playback failure to its user-facing message. [httpStatus] is null when the failure was not an HTTP status. */
fun describeError(errorCode: Int, httpStatus: Int?): ErrorInfo = when {
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        ErrorInfo("Server tidak merespons", Severity.Warning)
    httpStatus == 403 || httpStatus == 451 -> ErrorInfo("Dibatasi wilayah", Severity.Warning)
    httpStatus == 404 || httpStatus == 410 -> ErrorInfo("Channel tidak ditemukan", Severity.Error)
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        ErrorInfo("Tidak ada internet", Severity.Error)
    errorCode in UNSUPPORTED_FORMAT_CODES -> ErrorInfo("Format tidak didukung", Severity.Error)
    else -> ErrorInfo(
        "Channel tidak bisa diputar",
        Severity.Error,
        code = if (httpStatus != null) "HTTP $httpStatus · kode $errorCode" else "Kode $errorCode",
    )
}

private val UNSUPPORTED_FORMAT_CODES = setOf(
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)
