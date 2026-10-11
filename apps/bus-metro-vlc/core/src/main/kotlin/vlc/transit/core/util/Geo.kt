package vlc.transit.core.util

import kotlin.math.*

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(min(1.0, sqrt(a)))
}

data class BBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    fun contains(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon

    companion object {
        /** València and the reach of its commuter rail (Castelló to Gandia/Moixent, Utiel). */
        val VALENCIA_REGION = BBox(38.70, -1.45, 40.10, 0.25)
    }
}

/** Google encoded polyline (precision 1e5), compact enough to keep route shapes in SQLite. */
object Polyline {
    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var pLat = 0L
        var pLon = 0L
        for ((lat, lon) in points) {
            val la = (lat * 1e5).roundToLong()
            val lo = (lon * 1e5).roundToLong()
            write(sb, la - pLat); write(sb, lo - pLon)
            pLat = la; pLon = lo
        }
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Long) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }

    fun decode(s: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < s.length) {
            val (dLat, i2) = read(s, i); i = i2
            if (i >= s.length) break
            val (dLon, i3) = read(s, i); i = i3
            lat += dLat; lon += dLon
            out += lat / 1e5 to lon / 1e5
        }
        return out
    }

    private fun read(s: String, start: Int): Pair<Long, Int> {
        var i = start
        var result = 0L
        var shift = 0
        while (i < s.length) {
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val v = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return v to i
    }
}
