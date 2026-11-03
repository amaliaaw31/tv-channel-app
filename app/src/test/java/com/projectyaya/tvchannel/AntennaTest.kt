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
    fun antennaGroupComesFirstAmongGroups() {
        val tile = parseAntennaTile("""{"liveTv": [{"package": "a", "activity": "a.B"}]}""")!!
        val nasional = Channel("n", "Garuda TV", "ID", "general", null, "https://x/n.m3u8", emptyMap(), listOf("Nasional"))
        val sections = buildSections(listOf(nasional, tile))
        assertEquals(listOf("g:$ANTENNA_GROUP", "g:Nasional"), sections.map { it.key })
    }
}
