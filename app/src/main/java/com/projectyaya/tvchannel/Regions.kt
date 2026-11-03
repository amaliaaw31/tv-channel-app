package com.projectyaya.tvchannel

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** An Indonesian province: approximate geographic centre, and the TVRI regional feed that serves it. */
data class Province(val name: String, val lat: Double, val lon: Double, val tvriFeed: String)

// Centres are approximate and the lookup is "nearest centre", so it is right at province level in the
// middle of a province but can pick a neighbour close to a border. Provinces TVRI has no feed for
// (marked "no own feed") use the feed of the nearest province that has one.
val PROVINCES = listOf(
    Province("Aceh", 4.3, 96.9, "Aceh"),
    Province("Sumatera Utara", 2.0, 99.0, "NorthSumatra"),
    Province("Sumatera Barat", -0.8, 100.5, "WestSumatra"),
    Province("Riau", 0.5, 101.6, "Riau"),
    Province("Kepulauan Riau", 1.5, 105.5, "Riau"),                     // no own feed
    Province("Jambi", -1.6, 103.0, "Jambi"),
    Province("Sumatera Selatan", -3.2, 104.2, "SouthSumatra"),
    Province("Kepulauan Bangka Belitung", -2.5, 106.5, "BangkaBelitung"),
    Province("Bengkulu", -3.8, 102.3, "Bengkulu"),
    Province("Lampung", -4.9, 105.0, "Lampung"),
    Province("Banten", -6.4, 106.1, "Jakarta"),                         // no own feed
    Province("DKI Jakarta", -6.2, 106.85, "Jakarta"),
    Province("Jawa Barat", -6.9, 107.6, "WestJava"),
    Province("Jawa Tengah", -7.3, 110.0, "CentralJava"),
    Province("DI Yogyakarta", -7.9, 110.4, "Yogyakarta"),
    Province("Jawa Timur", -7.9, 112.3, "EastJava"),
    Province("Bali", -8.4, 115.2, "Bali"),
    Province("Nusa Tenggara Barat", -8.6, 117.4, "WestNusaTenggara"),
    Province("Nusa Tenggara Timur", -9.8, 121.5, "EastNusaTenggara"),
    Province("Kalimantan Barat", 0.0, 110.5, "WestKalimantan"),
    Province("Kalimantan Tengah", -1.7, 113.4, "CentralKalimantan"),
    Province("Kalimantan Selatan", -3.1, 115.3, "SouthKalimantan"),
    Province("Kalimantan Timur", 0.5, 116.5, "EastKalimantan"),
    Province("Kalimantan Utara", 3.0, 116.5, "EastKalimantan"),         // no own feed
    Province("Sulawesi Utara", 1.2, 124.5, "NorthSulawesi"),
    Province("Gorontalo", 0.7, 122.4, "Gorontalo"),
    Province("Sulawesi Tengah", -1.4, 121.4, "CentralSulawesi"),
    Province("Sulawesi Barat", -2.5, 119.4, "WestSulawesi"),
    Province("Sulawesi Selatan", -3.7, 120.0, "SouthSulawesi"),
    Province("Sulawesi Tenggara", -4.1, 122.1, "SoutheastSulawesi"),
    Province("Maluku", -3.0, 128.5, "Maluku"),
    Province("Maluku Utara", 1.5, 127.8, "Maluku"),                     // no own feed
    Province("Papua Barat", -1.3, 132.5, "WestPapua"),
    Province("Papua Barat Daya", -0.9, 131.3, "WestPapua"),             // no own feed
    Province("Papua", -3.5, 139.5, "Papua"),
    Province("Papua Tengah", -3.9, 136.5, "Papua"),                     // no own feed
    Province("Papua Pegunungan", -4.3, 139.0, "Papua"),                 // no own feed
    Province("Papua Selatan", -7.5, 140.0, "Papua"),                    // no own feed
)

// Farther than this from every centre means the TV is not in Indonesia: show nothing instead of a wrong region.
private const val MAX_DISTANCE_KM = 800.0

/** The province whose centre is closest to the position, or null when the position is not near Indonesia. */
fun nearestProvince(lat: Double, lon: Double): Province? =
    PROVINCES.minBy { distanceKm(lat, lon, it.lat, it.lon) }
        .takeIf { distanceKm(lat, lon, it.lat, it.lon) <= MAX_DISTANCE_KM }

/** Great-circle distance in km (haversine). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** The "Untuk Anda" section for a located province: its TVRI regional feed. */
fun forYouOf(province: Province) = ForYou("Untuk Anda · ${province.name}", setOf("TVRI.id#${province.tvriFeed}"))
