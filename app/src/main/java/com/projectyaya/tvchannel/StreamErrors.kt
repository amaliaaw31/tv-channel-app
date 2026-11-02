package com.projectyaya.tvchannel

import androidx.media3.common.PlaybackException

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
