package com.projectyaya.tvchannel

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Channel(
    val id: String,
    val name: String,
    /** ISO 3166-1 alpha-2, uppercase (ID, JP, US). */
    val country: String,
    val category: String,
    val logo: String?,
    val streamUrl: String,
    /** Extra HTTP request headers some streams require (User-Agent, Referer). */
    val headers: Map<String, String>,
)

private val ISO2 = Regex("[A-Z]{2}")

/**
 * Parses the channels.json array. Malformed JSON throws; an invalid or duplicate
 * entry is skipped (reported via [onSkip]) so one bad row cannot hide the whole list.
 */
fun parseChannels(json: String, onSkip: (String) -> Unit = {}): List<Channel> {
    val arr = JSONArray(json)
    val seen = HashSet<String>()
    val out = ArrayList<Channel>()
    for (i in 0 until arr.length()) {
        val ch = arr.optJSONObject(i)?.let(::toChannel)
        when {
            ch == null -> onSkip("entry #$i is invalid")
            !seen.add(ch.id) -> onSkip("duplicate id '${ch.id}'")
            else -> out += ch
        }
    }
    return out
}

// isNull covers both a missing key and an explicit JSON null.
private fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key).trim()

private fun toChannel(o: JSONObject): Channel? {
    val id = o.str("id")
    val name = o.str("name")
    val country = o.str("country")
    val category = o.str("category")
    val url = o.str("streamUrl")
    if (id.isEmpty() || name.isEmpty() || category.isEmpty()) return null
    if (!ISO2.matches(country)) return null
    // shortcut: scheme-only check; plain http also needs usesCleartextTraffic, add when such streams appear.
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null

    val headers = o.optJSONObject("headers")?.let { h ->
        h.keys().asSequence().associateWith { h.str(it) }.filterValues { it.isNotEmpty() }
    }.orEmpty()
    return Channel(id, name, country, category, o.str("logo").ifEmpty { null }, url, headers)
}

data class CountryGroup(val code: String, val name: String, val channels: List<Channel>)

/** Country name in Indonesian plus its code, e.g. "Indonesia (ID)"; just the code if the JVM has no name for it. */
fun countryName(code: String): String {
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.forLanguageTag("id"))
    return if (name.isBlank() || name == code) code else "$name ($code)"
}

/** Groups by country, sorted alphabetically by country name; channels keep their file order inside a group. */
fun groupByCountry(channels: List<Channel>): List<CountryGroup> =
    channels.groupBy { it.country }
        .map { (code, list) -> CountryGroup(code, countryName(code), list) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/** Channels in [category] (null = all) whose name contains [query] (trimmed, case-insensitive). */
fun filterChannels(channels: List<Channel>, category: String?, query: String): List<Channel> {
    val q = query.trim()
    return channels.filter {
        (category == null || it.category == category) && (q.isEmpty() || it.name.contains(q, ignoreCase = true))
    }
}

// Tab order and labels for the categories build-channels can produce; unknown ones go last, by id.
private val CATEGORY_LABELS = linkedMapOf(
    "sports" to "Olahraga",
    "documentary" to "Dokumenter",
    "science" to "Sains",
    "news" to "Berita",
    "travel" to "Wisata",
    "kids" to "Anak",
    "movies" to "Film",
)

fun categoryLabel(id: String): String = CATEGORY_LABELS[id] ?: id.replaceFirstChar { it.uppercase() }

/** Distinct categories present in [channels], in tab order. */
fun categoriesOf(channels: List<Channel>): List<String> {
    val order = CATEGORY_LABELS.keys.toList()
    return channels.map { it.category }.distinct()
        .sortedWith(compareBy({ order.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }, { it }))
}

enum class Source { Remote, Cache, Bundled }

class Loaded(val channels: List<Channel>, val source: Source)

/**
 * Picks the first usable source in order remote -> cache -> bundled. A source is usable when it
 * parses to at least one channel, so a broken download never replaces a good cache.
 * [onRemoteOk] receives the accepted remote text (to persist it as the new cache).
 */
fun pickChannels(
    remote: () -> String,
    cache: () -> String,
    bundled: () -> String,
    onSkip: (String) -> Unit = {},
    onRemoteOk: (String) -> Unit = {},
): Loaded {
    fun attempt(read: () -> String): Pair<String, List<Channel>>? =
        runCatching { read().let { it to parseChannels(it, onSkip) } }.getOrNull()?.takeIf { it.second.isNotEmpty() }

    attempt(remote)?.let { (text, list) ->
        onRemoteOk(text)
        return Loaded(list, Source.Remote)
    }
    attempt(cache)?.let { return Loaded(it.second, Source.Cache) }
    return Loaded(parseChannels(bundled(), onSkip), Source.Bundled) // throws only if even the bundled copy is broken
}

private const val TAG = "Channels"

/** Blocking; call off the main thread. Order: GitHub raw URL (refreshes the cache) -> cache -> bundled assets. */
fun loadChannels(context: Context): List<Channel> {
    val cacheFile = File(context.filesDir, "channels-cache.json")
    val start = SystemClock.elapsedRealtime()
    val loaded = pickChannels(
        remote = {
            try {
                download().also { Log.i(TAG, "remote: ${it.length / 1024} KB in ${SystemClock.elapsedRealtime() - start} ms") }
            } catch (e: Exception) {
                Log.w(TAG, "remote failed after ${SystemClock.elapsedRealtime() - start} ms: $e")
                throw e
            }
        },
        cache = { cacheFile.readText() },
        bundled = { context.assets.open("channels.json").bufferedReader().use { it.readText() } },
        onSkip = { Log.w(TAG, "skipped: $it") },
        onRemoteOk = { text ->
            // Write to a temp file and rename so a crash mid-write cannot leave a half-written cache.
            runCatching {
                val tmp = File(cacheFile.path + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(cacheFile)
            }.onFailure { Log.w(TAG, "cache write failed: $it") }
        },
    )
    Log.i(TAG, "loaded ${loaded.channels.size} channels from ${loaded.source} in ${SystemClock.elapsedRealtime() - start} ms")
    return loaded.channels
}

// shortcut: no ETag/If-Modified-Since, the whole file is re-downloaded on every start; add when it gets too big.
private fun download(): String {
    val conn = URL(BuildConfig.CHANNELS_URL).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 5_000
        conn.readTimeout = 10_000
        // A private repo's raw URL needs a token; see docs/channels-schema.md.
        if (BuildConfig.CHANNELS_TOKEN.isNotEmpty()) conn.setRequestProperty("Authorization", "token ${BuildConfig.CHANNELS_TOKEN}")
        check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
