package com.projectyaya.tvchannel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {
    // Unit tests run with the module dir (app/) as working directory.
    @Test
    fun bundledChannelsJsonIsFullyValid() {
        val skipped = mutableListOf<String>()
        val list = parseChannels(File("src/main/assets/channels.json").readText()) { skipped += it }
        assertEquals(emptyList<String>(), skipped)
        assertTrue(list.size in 3..5)
        assertTrue(list.any { it.headers.isNotEmpty() })
    }

    @Test
    fun skipsInvalidAndDuplicateEntries() {
        val skipped = mutableListOf<String>()
        val json = """[
          {"id":"a","name":"A","country":"ID","category":"news","streamUrl":"https://x/a.m3u8","headers":{"Referer":"https://r/"}},
          {"id":"a","name":"A again","country":"ID","category":"news","streamUrl":"https://x/a2.m3u8"},
          {"id":"b","name":"B","country":"idn","category":"news","streamUrl":"https://x/b.m3u8"},
          {"id":"c","name":"C","country":"JP","category":"news","streamUrl":"ftp://x/c"},
          {"id":"d","name":"D","country":"JP","category":null,"streamUrl":"https://x/d.m3u8"},
          "not an object"
        ]"""
        val list = parseChannels(json) { skipped += it }
        assertEquals(listOf("a"), list.map { it.id })
        assertEquals(mapOf("Referer" to "https://r/"), list[0].headers)
        assertNull(list[0].logo)
        assertEquals(5, skipped.size)
    }
}
