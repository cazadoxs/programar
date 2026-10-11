package vlc.transit.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import vlc.transit.core.feeds.Feeds
import vlc.transit.core.gtfs.*
import vlc.transit.core.realtime.RailRealtime
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipFile

/** Downloads and compiles the real feeds. Runs only with LIVE_FEEDS=1 (CI). */
class LiveFeedsTest {
    private fun live() = assumeTrue(System.getenv("LIVE_FEEDS") == "1")

    private fun download(urls: List<String>): File? {
        for (u in urls) {
            try {
                val c = URI(u).toURL().openConnection() as HttpURLConnection
                c.connectTimeout = 20_000; c.readTimeout = 60_000; c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "BusMetroVLC-ci")
                if (c.responseCode != 200) { println("  $u -> HTTP ${c.responseCode}"); continue }
                val f = File.createTempFile("feed", ".zip")
                c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } }
                println("  $u -> ${f.length()} bytes")
                return f
            } catch (e: Exception) { println("  $u -> ${e.javaClass.simpleName}: ${e.message}") }
        }
        return null
    }

    @Test fun compileAllRealFeeds() {
        live()
        val now = System.currentTimeMillis() / 1000
        for (src in Feeds.ALL) {
            println("== ${src.network}")
            val f = download(src.urls) ?: run { println("  UNAVAILABLE"); continue }
            val t0 = System.currentTimeMillis()
            val feed = ZipFile(f).use { z -> GtfsCompiler({ n -> z.getEntry(n)?.let(z::getInputStream) }, src.options).compile() }
            val bytes = feed.timetables.values.sumOf { it.size }
            println("  compiled in ${System.currentTimeMillis() - t0} ms: ${feed.stops.size} stops, ${feed.routes.size} routes, " +
                "${feed.patterns.size} patterns, ${feed.services.size} services, blobs=${bytes / 1024} KB, valid ${feed.validFrom}-${feed.validTo}")
            val cal = ServiceCalendar(feed.services, feed.validFrom, feed.validTo)
            val busiest = feed.stops.maxByOrNull { feed.timetables[it.id]?.size ?: 0 }!!
            val deps = Schedule.upcoming(feed.timetables.getValue(busiest.id), cal, now, horizonSec = 3600)
            println("  busiest stop ${busiest.id} ${busiest.name}: ${deps.size} departures in the next hour, approximate=${deps.firstOrNull()?.approximate}")
            deps.take(5).forEach { println("    ${feed.routes[it.routeIdx].shortName} -> ${feed.headsigns[it.headsignIdx]} at ${java.time.Instant.ofEpochSecond(it.epochSec)}") }
            check(feed.stops.isNotEmpty() && feed.routes.isNotEmpty())
            if (src.network == Network.RAIL) {
                val body = URI(Feeds.RAIL_TRIP_UPDATES).toURL().readText()
                val delays = RailRealtime.parseTripUpdates(body)
                val keys = feed.tripIds!!.map(RailRealtime::matchKey).toHashSet()
                println("  live trip updates: ${delays.size}, matching València trips: ${delays.keys.count { it in keys }}")
                val alerts = RailRealtime.parseAlerts(URI(Feeds.RAIL_ALERTS).toURL().readText(), feed.routes.map { it.id }.toSet(), "es")
                println("  València alerts: ${alerts.size}")
            }
            f.delete()
        }
    }
}
