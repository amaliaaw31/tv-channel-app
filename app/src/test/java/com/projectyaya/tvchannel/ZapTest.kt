package com.projectyaya.tvchannel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZapTest {
    private fun ch(id: String, name: String = id) = Channel(id, name, "ID", "general", null, "https://x/$id.m3u8", emptyMap(), emptyList())
    private val tile = Channel("tile", "TV Antena", "ID", "general", null, "", emptyMap(), emptyList(), launch = listOf(AppTarget("a", "a.B")))
    private val list = listOf(tile, ch("a"), ch("b"), ch("c"))

    @Test
    fun upIsNextAndDownIsPreviousWithWrapAround() {
        assertEquals("b", zapTarget(list, "a", +1)!!.id)
        assertEquals("c", zapTarget(list, "b", +1)!!.id)
        assertEquals("a", zapTarget(list, "c", +1)!!.id)     // wraps at the end
        assertEquals("c", zapTarget(list, "a", -1)!!.id)     // wraps at the start; the tile is skipped
        assertEquals("a", zapTarget(list, "b", -1)!!.id)
    }

    @Test
    fun nowhereToGoGivesNull() {
        assertNull(zapTarget(listOf(ch("a")), "a", +1))      // a single channel
        assertNull(zapTarget(list, "missing", +1))
        assertNull(zapTarget(list, "tile", +1))              // tiles are not part of the zap order
    }

    @Test
    fun positionCountsOnlyPlayableChannels() {
        assertEquals(1, zapPosition(list, "a"))
        assertEquals(3, zapPosition(list, "c"))
        assertEquals(0, zapPosition(list, "tile"))
    }

    @Test
    fun resumeFindsTheChannelByIdThenByName() {
        val channels = listOf(tile, ch("antena:1", "ANTV HD"), ch("x"))
        assertEquals("x", resolveResume(LastWatched("x", "whatever", "g:A"), channels)!!.id)
        // antenna ids change after a rescan, the display name does not
        assertEquals("antena:1", resolveResume(LastWatched("antena:99", "ANTV HD", "g:B"), channels)!!.id)
        assertNull(resolveResume(LastWatched("gone", "Gone", "g:A"), channels))
        assertNull(resolveResume(LastWatched("tile", "TV Antena", "g:A"), channels))   // a tile cannot be resumed
        assertNull(resolveResume(null, channels))
    }

    @Test
    fun resumeSectionComesFirstAndRepeatsTheChannel() {
        val a = ch("a").copy(groups = listOf("Nasional"))
        val sections = buildSections(listOf(a), resume = a)
        assertEquals(listOf(RESUME_KEY, "g:Nasional"), sections.map { it.key })
        assertEquals("Lanjutkan", sections[0].title)
        assertEquals(listOf("a"), sections[0].channels.map { it.id })
        assertEquals(listOf("g:Nasional"), buildSections(listOf(a)).map { it.key })
    }
}
