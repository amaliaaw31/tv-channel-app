package com.projectyaya.tvchannel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleLogicTest {
    private val en = TextTrack(0, "en")
    private val id = TextTrack(1, "id")
    private val unknown = TextTrack(2, null)
    private fun auto(langs: List<String>, vararg tracks: TextTrack) = planSubtitles(SubtitleMode.Auto, langs, tracks.toList())

    @Test
    fun a_indonesianChannelIsInactiveEvenWithTracks() {
        assertEquals(SubtitlePlan.Inactive, auto(listOf("ind"), en))
        assertEquals(SubtitlePlan.Inactive, auto(listOf("eng", "ind"), en, id))
        assertEquals(SubtitlePlan.Inactive, auto(listOf("ind")))                    // no tracks either: still nothing to draw
    }

    @Test
    fun b_indonesianTrackIsShownAsIs() {
        assertEquals(SubtitlePlan.Show(id, translate = false), auto(listOf("eng"), en, id))
        assertEquals(SubtitlePlan.Show(id, translate = false), auto(emptyList(), id))
        for (tag in listOf("id", "in", "ind", "id-ID")) {
            assertEquals(false, (auto(listOf("eng"), TextTrack(0, normalizeLang(tag))) as SubtitlePlan.Show).translate)
        }
    }

    @Test
    fun c_otherLanguageTrackIsTranslated() {
        assertEquals(SubtitlePlan.Show(en, translate = true), auto(listOf("eng"), en))
        assertEquals(SubtitlePlan.Show(unknown, translate = true), auto(emptyList(), unknown))   // tag unclear: detected later
        assertEquals(SubtitlePlan.Show(en, translate = true), auto(listOf("eng"), en, unknown)) // first track wins
    }

    @Test
    fun d_noTrackIsUnavailable() {
        assertEquals(SubtitlePlan.Unavailable, auto(listOf("eng")))
        assertEquals(SubtitlePlan.Unavailable, auto(emptyList()))
    }

    @Test
    fun otherModes() {
        val tracks = listOf(en, id)
        assertEquals(SubtitlePlan.Inactive, planSubtitles(SubtitleMode.Off, listOf("eng"), tracks))
        assertEquals(SubtitlePlan.Show(id, false), planSubtitles(SubtitleMode.Original, listOf("eng"), tracks))    // Indonesian track preferred
        assertEquals(SubtitlePlan.Show(en, false), planSubtitles(SubtitleMode.Original, listOf("ind"), listOf(en)))  // never translated
        assertEquals(SubtitlePlan.Show(en, true), planSubtitles(SubtitleMode.Translate, listOf("ind"), tracks))      // ignores the feed language
        assertEquals(SubtitlePlan.Show(id, false), planSubtitles(SubtitleMode.Translate, listOf("ind"), listOf(id)))
        assertEquals(SubtitlePlan.Unavailable, planSubtitles(SubtitleMode.Original, listOf("eng"), emptyList()))
        assertEquals(SubtitlePlan.Unavailable, planSubtitles(SubtitleMode.Translate, listOf("eng"), emptyList()))
    }

    @Test
    fun languageTagsAreNormalised() {
        assertEquals("en", normalizeLang("eng"))
        assertEquals("en", normalizeLang("en-US"))
        assertEquals("en", normalizeLang(" EN "))
        assertEquals("id", normalizeLang("in"))
        assertEquals("id", normalizeLang("ind"))
        assertEquals("ja", normalizeLang("jpn"))
        assertNull(normalizeLang("und"))
        assertNull(normalizeLang(""))
        assertNull(normalizeLang(null))
        assertNull(normalizeLang("xyzzy"))
        assertNull(normalizeLang("qqq"))
    }

    @Test
    fun perLineFilterLeavesIndonesianAndUnknownAlone() {
        assertEquals(LineAction.Original, lineAction("id", "en"))                // a line in Indonesian inside an English track
        assertEquals(LineAction.Original, lineAction(null, "en"))                // "und" / low confidence: show the text as it is
        assertEquals(LineAction.Translate("en"), lineAction("en", "en"))
        assertEquals(LineAction.Translate("fr"), lineAction("en", "fr"))         // the track tag is the source when it has one
        assertEquals(LineAction.Translate("es"), lineAction("es", null))         // no tag: the detected language
        assertNull(detectedLanguage("und"))
        assertEquals("id", detectedLanguage("id"))
    }

    @Test
    fun windowKeepsTheLastLinesAndSkipsRepeats() {
        var h = emptyList<String>()
        for (l in listOf("a", "a", "b", "c", "d")) h = pushLine(h, l)
        assertEquals(listOf("b", "c", "d"), h)                                   // "a" repeated once, oldest dropped at 3
        assertEquals("b\nc\nd", identificationWindow(h))                         // short lines: judged together
        assertEquals("", identificationWindow(emptyList()))
        val long = "Where are you going this evening my friend?"
        assertEquals(long, identificationWindow(pushLine(h, long)))              // a long newest line stands alone
    }

    @Test
    fun menuLineDescribesThePlan() {
        assertEquals("Subtitle tidak tersedia", describePlan(SubtitleMode.Auto, SubtitlePlan.Unavailable, listOf("eng")))
        assertEquals("Channel berbahasa Indonesia: tanpa terjemahan", describePlan(SubtitleMode.Auto, SubtitlePlan.Inactive, listOf("ind")))
        assertEquals("Subtitle mati", describePlan(SubtitleMode.Off, SubtitlePlan.Inactive, listOf("ind")))
        assertEquals("Diterjemahkan ke Indonesia (bahasa dideteksi)", describePlan(SubtitleMode.Auto, SubtitlePlan.Show(unknown, true), emptyList()))
    }
}
