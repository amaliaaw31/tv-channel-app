package com.projectyaya.tvchannel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** List section that holds the antenna tile. */
const val ANTENNA_GROUP = "TV Nasional (Antena)"

/**
 * The "TV Antena" tile, built from assets/antenna.json. It opens the TV's own Live TV app, where the
 * antenna channels (RCTI, SCTV, ...) live. Null when the file is missing or names no Live TV app.
 */
fun loadAntennaTile(context: Context): Channel? =
    runCatching { parseAntennaTile(context.assets.open("antenna.json").bufferedReader().use { it.readText() }) }.getOrNull()

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
