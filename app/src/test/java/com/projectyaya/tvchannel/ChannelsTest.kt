package com.projectyaya.tvchannel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {
    // Unit tests run with the module dir (app/) as working directory; data/ is the bundled channel list.
    private fun bundledJson() = File("../data/channels.json").readText()

    private fun one(id: String) =
        """[{"id":"$id","name":"$id","country":"ID","category":"news","streamUrl":"https://x/$id.m3u8"}]"""

    @Test
    fun bundledChannelsJsonIsFullyValid() {
        val skipped = mutableListOf<String>()
        val list = parseChannels(bundledJson()) { skipped += it }
        assertEquals(emptyList<String>(), skipped)
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun bundledSectionsCoverEveryChannelWithUniqueKeys() {
        val list = parseChannels(bundledJson())
        val sections = buildSections(list)
        assertEquals(sections.size, sections.map { it.key }.toSet().size)
        assertEquals(list.map { it.id }.toSet(), sections.flatMap { s -> s.channels.map { it.id } }.toSet())
        val rows = sections.flatMap { s -> s.channels.map { "${s.key}/${it.id}" } }
        assertEquals(rows.size, rows.toSet().size) // the list keys rows by section + id, so these must not repeat
    }

    @Test
    fun groupsComeFirstInFixedOrderThenCountries() {
        fun ch(id: String, name: String, country: String, groups: List<String> = emptyList()) =
            Channel(id, name, country, "news", null, "https://x/$id.m3u8", emptyMap(), groups)
        val list = listOf(
            ch("1", "Metro TV", "ID", listOf("Berita")),
            ch("2", "RCTI", "ID", listOf("Nasional", "MNC")),
            ch("3", "DW", "DE"),
            ch("4", "TVRI Bali", "ID", listOf("TVRI Daerah")),
            ch("5", "Zeta TV", "ID", listOf("Zeta")),
            ch("6", "CNA", "SG"),
            ch("7", "TVRI Aceh", "ID", listOf("TVRI Daerah")),
        )
        val sections = buildSections(list)
        val groupTitles = sections.takeWhile { it.key.startsWith("g:") }.map { it.title }
        assertEquals(listOf("Nasional", "MNC", "Berita", "TVRI Daerah", "Zeta"), groupTitles)  // fixed order, unknown last
        assertEquals(listOf("c:DE", "c:SG").sorted(), sections.drop(groupTitles.size).map { it.key }.sorted())
        assertTrue(sections.drop(groupTitles.size).all { it.key.startsWith("c:") })               // countries after groups
        assertEquals(listOf("TVRI Aceh", "TVRI Bali"), sections.first { it.title == "TVRI Daerah" }.channels.map { it.name }) // by name
        // RCTI is listed under both of its groups; a channel with groups is not repeated under its country
        assertEquals(2, sections.count { s -> s.channels.any { it.id == "2" } })
        assertTrue(sections.none { it.key == "c:ID" })
    }

    @Test
    fun parsesGroupsAndNote() {
        val json = """[
          {"id":"a","name":"A","country":"ID","category":"news","streamUrl":"https://x/a.m3u8",
           "groups":["Nasional"," MNC ","",null,"Nasional",7],"note":" perlu dicek di TV "},
          {"id":"b","name":"B","country":"ID","category":"news","streamUrl":"https://x/b.m3u8","groups":"Nasional"}
        ]"""
        val list = parseChannels(json)
        assertEquals(listOf("Nasional", "MNC", "7"), list[0].groups)   // trimmed, blanks/null dropped, de-duplicated
        assertEquals("perlu dicek di TV", list[0].note)
        assertEquals(emptyList<String>(), list[1].groups)               // not an array: ignored
        assertNull(list[1].note)
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

    private fun channel(id: String, name: String, category: String) =
        Channel(id, name, "ID", category, null, "https://x/$id.m3u8", emptyMap())

    @Test
    fun filtersByCategoryAndNameQuery() {
        val all = listOf(
            channel("1", "CNN International", "news"),
            channel("2", "Sport 24", "sports"),
            channel("3", "News Sport", "news"),
        )
        assertEquals(listOf("1", "2", "3"), filterChannels(all, null, "").map { it.id })
        assertEquals(listOf("1", "3"), filterChannels(all, "news", "  ").map { it.id })
        assertEquals(listOf("2", "3"), filterChannels(all, null, "SPORT").map { it.id })
        assertEquals(listOf("3"), filterChannels(all, "news", " sport ").map { it.id })
        assertEquals(emptyList<String>(), filterChannels(all, "sports", "cnn").map { it.id })
    }

    @Test
    fun categoriesFollowTabOrderWithUnknownLast() {
        val all = listOf("movies", "zeta", "sports", "alpha", "news", "sports").map { channel(it + "-id", it, it) }
        assertEquals(listOf("sports", "news", "movies", "alpha", "zeta"), categoriesOf(all))
        assertEquals("Olahraga", categoryLabel("sports"))
        assertEquals("Alpha", categoryLabel("alpha"))
    }

    @Test
    fun remoteWinsAndIsHandedToTheCache() {
        var cached: String? = null
        val loaded = pickChannels({ one("remote") }, { one("cache") }, { one("bundled") }, onRemoteOk = { cached = it })
        assertEquals(Source.Remote, loaded.source)
        assertEquals("remote", loaded.channels.single().id)
        assertEquals(one("remote"), cached)
    }

    @Test
    fun brokenRemoteFallsBackToCacheWithoutOverwritingIt() {
        var cacheWrites = 0
        val failures = listOf<() -> String>(
            { error("HTTP 404") },                                 // network/HTTP failure
            { "<html>not json</html>" },                           // garbage body
            { "[]" },                                              // parses but empty
            { """[{"id":"x"}]""" },                                // only invalid entries
        )
        for (remote in failures) {
            val loaded = pickChannels(remote, { one("cache") }, { one("bundled") }, onRemoteOk = { cacheWrites++ })
            assertEquals(Source.Cache, loaded.source)
        }
        assertEquals(0, cacheWrites)
    }

    @Test
    fun fallsBackToBundledWhenRemoteAndCacheAreUnusable() {
        val loaded = pickChannels({ error("offline") }, { throw java.io.FileNotFoundException() }, { one("bundled") })
        assertEquals(Source.Bundled, loaded.source)
        assertEquals("bundled", loaded.channels.single().id)
    }
}
