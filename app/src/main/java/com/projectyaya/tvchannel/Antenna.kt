package com.projectyaya.tvchannel

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log
import org.json.JSONObject

/** List section that holds the antenna tile and the antenna channels. */
const val ANTENNA_GROUP = "TV Nasional (Antena)"

private const val TAG = "Antenna"

// ---- config (assets/antenna.json) ------------------------------------------------------------

private fun readAsset(context: Context) = context.assets.open("antenna.json").bufferedReader().use { it.readText() }

/** The "TV Antena" tile: opens the TV's own Live TV app. Null when the file is missing or names no Live TV app. */
fun loadAntennaTile(context: Context): Channel? = runCatching { parseAntennaTile(readAsset(context)) }.getOrNull()

/** Names of the antenna channels to look for in the TV's scan. */
fun loadWantedChannels(context: Context): List<WantedChannel> = runCatching { parseWantedChannels(readAsset(context)) }.getOrDefault(emptyList())

/** Parses antenna.json: `tile.name`/`tile.note` and the `liveTv` candidates (package + activity), tried in order. */
fun parseAntennaTile(json: String): Channel? {
    val root = JSONObject(json)
    val candidates = root.optJSONArray("liveTv")?.let { arr ->
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { o ->
            val pkg = o.optString("package").trim()
            val activity = o.optString("activity").trim()
            if (pkg.isEmpty() || activity.isEmpty()) null else AppTarget(pkg, activity)
        }
    }.orEmpty()
    if (candidates.isEmpty()) return null
    val tile = root.optJSONObject("tile")
    val name = tile?.optString("name")?.trim().orEmpty().ifEmpty { "TV Antena" }
    val note = tile?.optString("note")?.trim().orEmpty().ifEmpty { null }
    return Channel("antena:tv", name, "ID", "general", null, "", emptyMap(), listOf(ANTENNA_GROUP), note, candidates)
}

/** A channel to find in the scan, by display name; [aliases] are other names the scan may use for it. */
data class WantedChannel(val name: String, val aliases: List<String> = emptyList())

/** Parses `channels`: each element is a name or `{"name", "aliases": [...]}`. Blank names are dropped. */
fun parseWantedChannels(json: String): List<WantedChannel> {
    val arr = JSONObject(json).optJSONArray("channels") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val item = arr.opt(i)
        when (item) {
            is String -> item.trim().takeIf { it.isNotEmpty() }?.let { WantedChannel(it) }
            is JSONObject -> item.optString("name").trim().takeIf { it.isNotEmpty() }?.let { name ->
                val aliases = item.optJSONArray("aliases")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()
                WantedChannel(name, aliases)
            }
            else -> null
        }
    }
}

// ---- the TV's scan (TvProvider) --------------------------------------------------------------

/** One channel from the TV's antenna scan. */
data class ScanChannel(val id: Long, val number: String, val name: String, val inputId: String)

/**
 * Channels found by the TV's antenna scan. Needs READ_TV_LISTINGS (runtime permission); without it,
 * or when nothing was scanned, the list is empty. IDs are read fresh because a rescan renumbers them.
 */
@SuppressLint("MissingPermission")
fun readScan(context: Context): List<ScanChannel> = runCatching {
    val projection = arrayOf(
        TvContract.Channels._ID,
        TvContract.Channels.COLUMN_DISPLAY_NUMBER,
        TvContract.Channels.COLUMN_DISPLAY_NAME,
        TvContract.Channels.COLUMN_INPUT_ID,
        TvContract.Channels.COLUMN_TYPE,
    )
    context.contentResolver.query(TvContract.Channels.CONTENT_URI, projection, null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val type = c.getString(4).orEmpty()
                val inputId = c.getString(3).orEmpty()
                // Home-screen preview rows and other apps' channels have no tuner input.
                if (inputId.isEmpty() || type == TvContract.Channels.TYPE_PREVIEW || type == TvContract.Channels.TYPE_OTHER) continue
                add(ScanChannel(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), inputId))
            }
        }
    }.orEmpty()
}.onFailure { Log.w(TAG, "cannot read the TV scan: ${it.javaClass.simpleName}") }.getOrDefault(emptyList())

// Letters and digits only, lowercase, trailing "hd" dropped: "TRANS 7 HD" == "Trans7", "tvOne" == "TVONE HD".
private fun squash(name: String) = name.lowercase().filter { it.isLetterOrDigit() }.removeSuffix("hd")

fun matchesScanName(wanted: WantedChannel, scanName: String): Boolean {
    val scan = squash(scanName)
    return scan.isNotEmpty() && (listOf(wanted.name) + wanted.aliases).any { squash(it) == scan }
}

/**
 * The wanted channels that the scan has, in the wanted order, as list items played through a TvView.
 * The shown name is the TV's own display_name (e.g. "RCTI HD"), so it matches what Live TV calls the channel.
 */
fun antennaChannels(scan: List<ScanChannel>, wanted: List<WantedChannel>): List<Channel> =
    wanted.mapNotNull { w ->
        scan.firstOrNull { matchesScanName(w, it.name) }?.let { s ->
            Channel(
                id = "antena:${s.id}", name = s.name.trim(), country = "ID", category = "general", logo = null, streamUrl = "",
                headers = emptyMap(), groups = listOf(ANTENNA_GROUP), note = "no. ${s.number}", antenna = AntennaRef(s.id, s.inputId),
            )
        }
    }

/**
 * Whether antenna channels are also tuned into the small preview slot while focused. Default false: the tuner video
 * is drawn by the TV's own video plane, which does not follow a small TvView's position, so by default it is only
 * shown fullscreen (docs/sources.md, "Siaran antena").
 */
fun loadAntennaPreview(context: Context): Boolean = runCatching { JSONObject(readAsset(context)).optBoolean("preview", false) }.getOrDefault(false)

// ---- Live TV tile ----------------------------------------------------------------------------

/** Starts the first Live TV app that exists on this TV; false when none could be started. */
fun launchLiveTv(context: Context, targets: List<AppTarget>): Boolean {
    for (target in targets) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setClassName(target.pkg, target.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // not installed on this TV: try the next candidate
        } catch (_: SecurityException) {
            // present but not launchable by us: same
        }
    }
    return false
}
