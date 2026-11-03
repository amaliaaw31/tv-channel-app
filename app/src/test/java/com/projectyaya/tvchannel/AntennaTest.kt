package com.projectyaya.tvchannel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntennaTest {
    @Test
    fun bundledAntennaConfigIsValid() {
        val tile = parseAntennaTile(File("../data/antenna.json").readText())
        assertNotNull(tile)
        assertEquals(listOf(ANTENNA_GROUP), tile!!.groups)
        assertTrue(tile.launch.isNotEmpty())
    }

    @Test
    fun parsesTileAndKeepsOnlyCompleteCandidatesInOrder() {
        val json = """{
          "tile": {"name": " TV Antena ", "note": "buka Live TV"},
          "liveTv": [
            {"package": "a.b", "activity": "a.b.Main"},
            {"package": "", "activity": "x.Y"},
            {"package": "c.d"},
            "not an object",
            {"package": "e.f", "activity": "e.f.Live"}
          ]
        }"""
        val tile = parseAntennaTile(json)!!
        assertEquals("TV Antena", tile.name)
        assertEquals("buka Live TV", tile.note)
        assertEquals(listOf(AppTarget("a.b", "a.b.Main"), AppTarget("e.f", "e.f.Live")), tile.launch)
        assertEquals("", tile.streamUrl)                 // no stream: OK opens the app instead of playing
    }

    @Test
    fun defaultsAndMissingLiveTv() {
        assertEquals("TV Antena", parseAntennaTile("""{"liveTv": [{"package": "a", "activity": "a.B"}]}""")!!.name)
        assertNull(parseAntennaTile("""{"tile": {"name": "X"}}"""))                 // no Live TV app named
        assertNull(parseAntennaTile("""{"liveTv": []}"""))
    }

    @Test
    fun parsesWantedChannelNamesAndAliases() {
        val json = """{"channels": ["RCTI", " ", {"name": "Trans7", "aliases": ["Trans 7", ""]}, {"aliases": ["x"]}, 5, "tvOne"]}"""
        assertEquals(
            listOf(WantedChannel("RCTI"), WantedChannel("Trans7", listOf("Trans 7")), WantedChannel("tvOne")),
            parseWantedChannels(json),
        )
        assertEquals(emptyList<WantedChannel>(), parseWantedChannels("""{"liveTv": []}"""))
        assertTrue(parseWantedChannels(File("../data/antenna.json").readText()).isNotEmpty())
    }

    @Test
    fun scanNamesMatchLooselyButNotLoosely() {
        fun m(wanted: String, scan: String) = matchesScanName(WantedChannel(wanted), scan)
        assertTrue(m("RCTI", "RCTI HD"))
        assertTrue(m("Trans7", "TRANS 7 HD"))
        assertTrue(m("tvOne", "TVONE HD"))
        assertTrue(m("Metro TV", "METRO TV HD"))
        assertTrue(m("iNews", "iNews HD"))
        assertTrue(!m("RCTI", "RCTI Plus"))          // a different channel is not a match
        assertTrue(!m("MNCTV", "MAGNA CHANNEL"))
        assertTrue(!m("RCTI", ""))
        assertTrue(matchesScanName(WantedChannel("Kompas", listOf("Kompas TV")), "KOMPASTV HD")) // alias
    }

    @Test
    fun antennaChannelsFollowWantedOrderAndSkipMissingOnes() {
        val scan = listOf(
            ScanChannel(445, "21", "TRANS 7 HD", "tuner/HW0"),
            ScanChannel(452, "28", "RCTI HD", "tuner/HW0"),
            ScanChannel(454, "30", "GTV HD", "tuner/HW0"),
        )
        val items = antennaChannels(scan, listOf(WantedChannel("RCTI"), WantedChannel("SCTV"), WantedChannel("Trans7")))
        assertEquals(listOf("RCTI HD", "TRANS 7 HD"), items.map { it.name })          // the TV's own display_name; SCTV is not in the scan
        assertEquals(AntennaRef(452, "tuner/HW0"), items[0].antenna)
        assertEquals("no. 28", items[0].note)
        assertEquals(listOf(ANTENNA_GROUP), items[0].groups)
        assertEquals("", items[0].streamUrl)
    }

    @Test
    fun antennaGroupHoldsTheTileFirstThenChannels() {
        val tile = parseAntennaTile("""{"liveTv": [{"package": "a", "activity": "a.B"}]}""")!!
        val scan = listOf(ScanChannel(1, "1", "ANTV HD", "in"), ScanChannel(2, "2", "RCTI HD", "in"))
        val sections = buildSections(listOf(tile) + antennaChannels(scan, listOf(WantedChannel("RCTI"), WantedChannel("ANTV"))))
        assertEquals(listOf("TV Antena", "ANTV HD", "RCTI HD"), sections.single().channels.map { it.name })
    }

    @Test
    fun displayNameIsTrimmedAndNotTheConfiguredName() {
        val items = antennaChannels(listOf(ScanChannel(1, "5", "CNN INDONESIA HD ", "in")), listOf(WantedChannel("CNN Indonesia")))
        assertEquals("CNN INDONESIA HD", items.single().name)
    }

    @Test
    fun antennaPreviewIsOffUnlessConfigured() {
        // the flag is read with optBoolean(..., false): missing or wrong type means off
        assertEquals(false, org.json.JSONObject("""{"liveTv": []}""").optBoolean("preview", false))
        assertEquals(true, org.json.JSONObject("""{"preview": true}""").optBoolean("preview", false))
        assertEquals(false, File("../data/antenna.json").readText().let { org.json.JSONObject(it).optBoolean("preview", false) })
    }

    @Test
    fun antennaGroupComesFirstAmongGroups() {
        val tile = parseAntennaTile("""{"liveTv": [{"package": "a", "activity": "a.B"}]}""")!!
        val nasional = Channel("n", "Garuda TV", "ID", "general", null, "https://x/n.m3u8", emptyMap(), listOf("Nasional"))
        val sections = buildSections(listOf(nasional, tile))
        assertEquals(listOf("g:$ANTENNA_GROUP", "g:Nasional"), sections.map { it.key })
    }
}
