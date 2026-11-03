package com.projectyaya.tvchannel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionsTest {
    private fun province(lat: Double, lon: Double) = nearestProvince(lat, lon)?.name

    @Test
    fun citiesMapToTheirProvince() {
        assertEquals("DKI Jakarta", province(-6.2476, 106.8193))   // the TV's own location
        assertEquals("Jawa Barat", province(-6.91, 107.61))        // Bandung
        assertEquals("Jawa Timur", province(-7.25, 112.75))        // Surabaya
        assertEquals("Sumatera Utara", province(3.59, 98.67))      // Medan
        assertEquals("Bali", province(-8.65, 115.22))              // Denpasar
        assertEquals("Papua", province(-2.53, 140.72))             // Jayapura
        assertEquals("Papua Selatan", province(-8.49, 140.40))     // Merauke
    }

    @Test
    fun farFromIndonesiaGivesNothing() {
        assertNull(province(-33.87, 151.21))   // Sydney
        assertNull(province(51.5, -0.12))      // London
    }

    @Test
    fun distanceIsSane() {
        assertEquals(0.0, distanceKm(-6.2, 106.8, -6.2, 106.8), 1e-9)
        assertEquals(114.0, distanceKm(-6.2, 106.85, -6.9, 107.6), 5.0)   // Jakarta centre to Jawa Barat centre
    }

    @Test
    fun everyProvinceHasARealTvriFeedInTheBundledData() {
        val ids = parseChannels(File("../data/channels.json").readText()).map { it.id }.toSet()
        assertEquals(38, PROVINCES.size)
        assertEquals(PROVINCES.size, PROVINCES.map { it.name }.toSet().size)
        val missing = PROVINCES.filter { "TVRI.id#${it.tvriFeed}" !in ids }.map { it.name to it.tvriFeed }
        assertEquals(emptyList<Pair<String, String>>(), missing)      // catches a typo in a feed name
    }

    @Test
    fun forYouSectionComesFirstAndFollowsTheFilter() {
        fun ch(id: String, name: String, groups: List<String>) =
            Channel(id, name, "ID", "general", null, "https://x/$id.m3u8", emptyMap(), groups)
        val list = listOf(ch("TVRI.id#Jakarta", "TVRI Jakarta", listOf("TVRI Daerah")), ch("TVRI.id#Bali", "TVRI Bali", listOf("TVRI Daerah")))
        val forYou = forYouOf(nearestProvince(-6.2476, 106.8193)!!)
        assertEquals("Untuk Anda · DKI Jakarta", forYou.title)

        val sections = buildSections(list, forYou)
        assertEquals(listOf("local", "g:TVRI Daerah"), sections.map { it.key })
        assertEquals(listOf("TVRI Jakarta"), sections[0].channels.map { it.name })
        assertTrue(sections[1].channels.any { it.id == "TVRI.id#Jakarta" })   // still in its normal group too

        // filtered so that the local channel is hidden: no empty "Untuk Anda" section
        assertEquals(listOf("g:TVRI Daerah"), buildSections(list.filter { it.name == "TVRI Bali" }, forYou).map { it.key })
        assertEquals(listOf("g:TVRI Daerah"), buildSections(list).map { it.key })   // no location: unchanged
    }
}
