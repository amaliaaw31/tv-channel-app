package com.projectyaya.tvchannel

import java.util.Locale

/** The subtitle menu choices. [Auto] follows the rules in [planSubtitles]. */
enum class SubtitleMode(val label: String) {
    Auto("Otomatis"),
    Off("Mati"),
    Original("Asli"),
    Translate("Selalu terjemahkan"),
}

/** A text track of the playing stream; [language] is already normalised by [normalizeLang] (null = unknown). */
data class TextTrack(val index: Int, val language: String?)

sealed interface SubtitlePlan {
    /** Nothing is drawn and nothing is downloaded: mode "Mati", or an Indonesian channel in "Otomatis". */
    data object Inactive : SubtitlePlan

    /** The stream has no text track. */
    data object Unavailable : SubtitlePlan

    /** Draw [track]; with [translate] its lines go through the translator. */
    data class Show(val track: TextTrack, val translate: Boolean) : SubtitlePlan
}

// The JVM still lists the legacy codes in (iw, ji) next to the modern ones; map those to the modern code.
private val LEGACY = mapOf("in" to "id", "iw" to "he", "ji" to "yi")

private val ISO3_TO_ISO2: Map<String, String> by lazy {
    Locale.getISOLanguages().filter { it !in LEGACY }.associateBy { Locale.forLanguageTag(it).isO3Language }
}

/**
 * Two-letter lowercase code for a language tag ("en", "eng", "en-US", "in", "ind" -> "en", "en", "en", "id", "id"),
 * or null for empty, "und" and anything unknown, so callers fall back to detecting the language from the text.
 */
fun normalizeLang(tag: String?): String? {
    val base = tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_').orEmpty()
    return when {
        base.isEmpty() || base == "und" || base == "mis" || base == "zxx" -> null
        base.length == 2 -> LEGACY[base] ?: base
        base.length == 3 -> ISO3_TO_ISO2[base]
        else -> null
    }
}

fun isIndonesian(lang: String?) = lang == "id"

/**
 * What to show for a stream, decided when fullscreen opens (and again when its tracks arrive):
 *  Auto: (a) feed language "ind" -> [SubtitlePlan.Inactive]; (b) an Indonesian track -> shown as is;
 *        (c) any other track -> translated; (d) no track -> [SubtitlePlan.Unavailable].
 *  Off: nothing. Original: an Indonesian track if there is one, else the first, never translated.
 *  Translate: the first non-Indonesian track translated (an Indonesian-only stream is shown as is).
 * [channelLanguages] are ISO 639-3 codes from channels.json; [tracks] are in the stream's own order.
 */
fun planSubtitles(mode: SubtitleMode, channelLanguages: List<String>, tracks: List<TextTrack>): SubtitlePlan {
    if (mode == SubtitleMode.Off) return SubtitlePlan.Inactive
    val indonesian = tracks.firstOrNull { isIndonesian(it.language) }
    return when (mode) {
        SubtitleMode.Auto -> when {
            "ind" in channelLanguages -> SubtitlePlan.Inactive
            indonesian != null -> SubtitlePlan.Show(indonesian, translate = false)
            tracks.isNotEmpty() -> SubtitlePlan.Show(tracks.first(), translate = true)
            else -> SubtitlePlan.Unavailable
        }
        SubtitleMode.Original -> (indonesian ?: tracks.firstOrNull())?.let { SubtitlePlan.Show(it, translate = false) } ?: SubtitlePlan.Unavailable
        SubtitleMode.Translate -> {
            val foreign = tracks.firstOrNull { !isIndonesian(it.language) }
            when {
                foreign != null -> SubtitlePlan.Show(foreign, translate = true)
                indonesian != null -> SubtitlePlan.Show(indonesian, translate = false)
                else -> SubtitlePlan.Unavailable
            }
        }
        SubtitleMode.Off -> SubtitlePlan.Inactive
    }
}

sealed interface LineAction {
    /** Show the line as it is. */
    data object Original : LineAction

    /** Translate the line from [from] (two-letter code); null = let the translator side detect it. */
    data class Translate(val from: String?) : LineAction
}

/** What language identification made of some text: a two-letter [language] (null = "und") and its [confidence] 0..1. */
data class Detection(val language: String?, val confidence: Float)

/** A line with fewer words than this is only translated when its language is detected confidently. */
const val MIN_WORDS = 3

/** The confidence a very short line needs ("Ya", "OK", a name) before it is translated. */
const val SHORT_LINE_CONFIDENCE = 0.8f

fun wordCount(line: String): Int = line.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

/**
 * Per-line filter for mixed channels. [detected] is the language of the last few lines (null for "und" or a
 * low-confidence guess). An Indonesian line is left alone, and so is one whose language is not known: it is
 * better to show the original text than to translate it from a wrong language. A line of fewer than [MIN_WORDS]
 * words is also left alone unless the detection is at least [SHORT_LINE_CONFIDENCE] sure, since a word or two
 * says little about the language. The source language comes from the track tag when it has one, else from the detection.
 */
fun lineAction(line: String, detected: Detection?, trackLanguage: String?): LineAction {
    val language = detected?.language
    return when {
        language == null || isIndonesian(language) -> LineAction.Original
        wordCount(line) < MIN_WORDS && detected.confidence < SHORT_LINE_CONFIDENCE -> LineAction.Original
        else -> LineAction.Translate(trackLanguage ?: language)
    }
}

/** Remembers the last [max] distinct consecutive lines (roll-up captions repeat the same line). */
fun pushLine(history: List<String>, line: String, max: Int = WINDOW_LINES): List<String> =
    if (history.lastOrNull() == line) history else (history + line).takeLast(max)

const val WINDOW_LINES = 3

/** A line at least this long is identified on its own; shorter ones are too unreliable (and "OK" says nothing). */
const val STANDALONE_CHARS = 30

/**
 * The text language identification looks at: the last few lines together, since a short line is unreliable.
 * A long newest line is used alone, so one foreign sentence is not pulled toward the language of the lines before it.
 */
fun identificationWindow(history: List<String>): String {
    val last = history.lastOrNull().orEmpty()
    return if (last.length >= STANDALONE_CHARS) last else history.takeLast(WINDOW_LINES).joinToString("\n")
}

/** One line for the menu saying what is going on. */
fun describePlan(mode: SubtitleMode, plan: SubtitlePlan, channelLanguages: List<String>): String = when (plan) {
    SubtitlePlan.Unavailable -> "Subtitle tidak tersedia"
    SubtitlePlan.Inactive -> when {
        mode == SubtitleMode.Off -> "Subtitle mati"
        "ind" in channelLanguages -> "Channel berbahasa Indonesia: tanpa terjemahan"
        else -> "Subtitle mati"
    }
    is SubtitlePlan.Show -> when {
        plan.translate -> "Diterjemahkan ke Indonesia" + (plan.track.language?.let { " dari ${Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("id"))}" } ?: " (bahasa dideteksi)")
        isIndonesian(plan.track.language) -> "Subtitle Indonesia dari siaran"
        else -> "Subtitle asli dari siaran"
    }
}
