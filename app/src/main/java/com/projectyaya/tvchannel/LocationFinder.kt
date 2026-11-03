package com.projectyaya.tvchannel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val FIX_TIMEOUT_MS = 8_000L

/**
 * The province the TV is in, or null (location off, no fix in time, or not in Indonesia).
 * Uses the network provider only (Wi-Fi based, coarse): TVs have no GPS chip. The position is used
 * on the device to pick a region and is never stored, logged or sent anywhere.
 * The caller must hold ACCESS_COARSE_LOCATION.
 */
@SuppressLint("MissingPermission")
suspend fun findProvince(context: Context): Province? {
    // Logs say which step failed, never the position itself.
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
        Log.i(TAG, "no province: location service off or missing")
        return null
    }
    val location = lastKnown(manager) ?: current(context, manager)
    if (location == null) {
        Log.i(TAG, "no province: no location fix")
        return null
    }
    val province = nearestProvince(location.latitude, location.longitude)
    Log.i(TAG, "province: ${province?.name ?: "none (not near Indonesia)"}")
    return province
}

private const val TAG = "Region"

@SuppressLint("MissingPermission")
private fun lastKnown(manager: LocationManager): Location? =
    listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }

@SuppressLint("MissingPermission")
private suspend fun current(context: Context, manager: LocationManager): Location? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
    suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        runCatching {
            LocationManagerCompat.getCurrentLocation(manager, LocationManager.NETWORK_PROVIDER, signal, ContextCompat.getMainExecutor(context)) {
                cont.resume(it)
            }
        }.onFailure { cont.resume(null) } // provider missing or disabled
    }
}
