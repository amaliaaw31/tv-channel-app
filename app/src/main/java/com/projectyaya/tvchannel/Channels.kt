package com.projectyaya.tvchannel

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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

/** Blocking read from local assets; call off the main thread. Swap for the raw GitHub URL later. */
fun loadChannels(context: Context): List<Channel> {
    val json = context.assets.open("channels.json").bufferedReader().use { it.readText() }
    return parseChannels(json) { Log.w("Channels", "skipped: $it") }
}
