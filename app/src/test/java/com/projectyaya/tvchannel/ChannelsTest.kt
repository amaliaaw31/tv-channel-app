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
    fun groupsAreSortedByCountryNameAndKeepEveryChannel() {
        val list = parseChannels(bundledJson())
        val groups = groupByCountry(list)
        val names = groups.map { it.name }
        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
        assertEquals(list.size, groups.sumOf { it.channels.size })
        assertEquals(groups.size, groups.map { it.code }.toSet().size)
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
