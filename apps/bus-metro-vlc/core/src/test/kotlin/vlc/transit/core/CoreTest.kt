package vlc.transit.core

import org.junit.Assert.*
import org.junit.Test
import vlc.transit.core.gtfs.*
import vlc.transit.core.realtime.BusRealtime
import vlc.transit.core.realtime.RailRealtime
import vlc.transit.core.util.*
import java.time.LocalDate
import java.time.LocalDateTime

class CoreTest {

    private val feed = mapOf(
        "agency.txt" to "agency_id,agency_name,agency_url,agency_timezone\nA,Test,https://x,Europe/Madrid\n",
        "stops.txt" to """
            stop_id,stop_code,stop_name,stop_lat,stop_lon,location_type,parent_station
            S1,101,"Plaça de l'Ajuntament,  Centre",39.4699,-0.3763,0,
            ST,ST,Estació Àngel Guimerà,39.4700,-0.3900,1,
            P1,,Àngel Guimerà V1,39.4701,-0.3901,0,ST
            P2,,Àngel Guimerà V2,39.4702,-0.3902,0,ST
            FAR,FAR,Madrid,40.4,-3.7,0,
        """.trimIndent() + "\n",
        "routes.txt" to "﻿route_id,agency_id,route_short_name,route_long_name,route_type,route_color,route_text_color\nR1,A,4  ,Port - Centre,3,FE0000,FFFFFF\nR2,A,X,Unused,3,,\n",
        "trips.txt" to "route_id,service_id,trip_id,trip_headsign,direction_id,shape_id\nR1,WK,T1,,0,SH1\nR1,WK,T2,Centre,0,SH1\nR1,SAT,T3,Centre,0,\nR1,WK,TN,Centre,0,\n",
        "stop_times.txt" to """
            trip_id,arrival_time,departure_time,stop_id,stop_sequence
            T1,08:00:00,08:00:00,S1,1
            T1,08:05:00,08:05:00,P1,2
            T1,08:10:00,08:10:00,FAR,3
            T2,09:00:00,09:00:00,S1,1
            T2,09:06:00,09:06:00,P2,2
            T3,10:00:00,10:00:00,S1,1
            TN,24:30:00,24:30:00,S1,1
        """.trimIndent() + "\n",
        "calendar.txt" to "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nWK,1,1,1,1,1,0,0,20261001,20261031\nSAT,0,0,0,0,0,1,0,20261001,20261031\n",
        "calendar_dates.txt" to "service_id,date,exception_type\nWK,20261012,2\n",
        "shapes.txt" to "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\nSH1,39.4699,-0.3763,1\nSH1,39.4750,-0.3800,2\nSH1,39.4700,-0.3900,3\n",
    )

    private fun compile(keepTrips: Boolean = false) = GtfsCompiler(
        { name -> feed[name]?.byteInputStream() },
        CompileOptions(Network.BUS, keepTripIds = keepTrips),
    ).compile()

    @Test fun csvHandlesQuotesPaddingAndBom() {
        val r = CsvReader("﻿a,b ,c\n\"x, \"\"y\"\"\",  2  ,\r\n".byteInputStream())
        assertEquals(listOf("a", "b", "c"), r.header)
        r.forEachRow { row -> assertEquals("x, \"y\"", row[0]); assertEquals("2", row[1]); assertEquals("", row[2]) }
    }

    @Test fun gtfsTimeParsing() {
        assertEquals(8 * 3600 + 5 * 60, parseGtfsTime("08:05:00"))
        assertEquals(24 * 3600 + 1800, parseGtfsTime("24:30:00"))
        assertEquals(6 * 3600, parseGtfsTime(" 6:00:00"))
        assertEquals(-1, parseGtfsTime("8h"))
        assertEquals(-1, parseGtfsTime(""))
    }

    @Test fun compilerFoldsPlatformsDropsFarStopsAndUnusedRoutes() {
        val c = compile()
        assertEquals(setOf("S1", "ST"), c.stops.map { it.id }.toSet())
        assertEquals(listOf("4"), c.routes.map { it.shortName })
        val s1 = c.stops.first { it.id == "S1" }
        assertEquals("Plaça de l'Ajuntament, Centre", s1.name)
        // T1 had no headsign: it falls back to the last stop's name, even outside the area.
        assertTrue("Madrid" in c.headsigns)
        assertEquals(20261001, c.validFrom); assertEquals(20261031, c.validTo)
        val pattern = c.patterns.first { c.headsigns[it.headsignIdx] == "Madrid" }
        assertEquals(listOf("S1", "ST"), pattern.stopIds)
        assertNotNull(pattern.shape)
        assertEquals(3, Polyline.decode(pattern.shape!!).size)
    }

    @Test fun timetableRoundTrip() {
        val g = Timetable.Group(1, 2, 3).apply { secs += listOf(500, 100, 300); trips += listOf(7, 8, 9) }
        val back = Timetable.decodeAll(Timetable.encode(listOf(g), withTrips = true))
        assertEquals(listOf(100, 300, 500), back.map { it.secs })
        assertEquals(listOf(8, 9, 7), back.map { it.tripIdx })
        assertTrue(back.all { it.routeIdx == 1 && it.headsignIdx == 2 && it.serviceIdx == 3 })
    }

    @Test(expected = IllegalStateException::class)
    fun truncatedBlobThrows() {
        val blob = Timetable.encode(listOf(Timetable.Group(0, 0, 0).apply { secs += 1000 }), false)
        Timetable.decodeAll(blob.copyOf(blob.size - 1))
    }

    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(VALENCIA_ZONE).toEpochSecond()

    @Test fun scheduleUsesCalendarExceptionsAndAfterMidnightTrips() {
        val c = compile()
        val cal = ServiceCalendar(c.services, c.validFrom, c.validTo)
        val blob = c.timetables.getValue("S1")
        // Tuesday 13 Oct 07:50 -> T1 08:00 and T2 09:00
        val tue = Schedule.upcoming(blob, cal, epoch(2026, 10, 13, 7, 50))
        assertEquals(listOf(epoch(2026, 10, 13, 8, 0), epoch(2026, 10, 13, 9, 0)), tue.map { it.epochSec })
        // Monday 12 Oct is removed by calendar_dates
        assertTrue(Schedule.upcoming(blob, cal, epoch(2026, 10, 12, 7, 50)).isEmpty())
        // 00:20 on Wednesday sees Tuesday's 24:30 trip
        val night = Schedule.upcoming(blob, cal, epoch(2026, 10, 14, 0, 20), horizonSec = 1800)
        assertEquals(listOf(epoch(2026, 10, 14, 0, 30)), night.map { it.epochSec })
        // Saturday only has T3
        assertEquals(1, Schedule.upcoming(blob, cal, epoch(2026, 10, 17, 9, 30)).size)
    }

    @Test fun lapsedFeedFallsBackToSameWeekday() {
        val c = compile()
        val cal = ServiceCalendar(c.services, c.validFrom, c.validTo)
        val r = cal.resolve(LocalDate.of(2026, 12, 15)) // a Tuesday after the feed ends
        assertTrue(r.approximate)
        // Tuesdays in range: 6, 13, 20, 27 Oct; all equally busy, so the latest wins.
        assertEquals(LocalDate.of(2026, 10, 27), r.date)
        val deps = Schedule.upcoming(c.timetables.getValue("S1"), cal, epoch(2026, 12, 15, 7, 50))
        assertEquals(2, deps.size)
        assertTrue(deps.all { it.approximate })
    }

    @Test fun keepsDenseTripIdsForRealtime() {
        val c = compile(keepTrips = true)
        val trips = c.tripIds!!
        val entries = Timetable.decodeAll(c.timetables.getValue("S1"))
        assertEquals(setOf("T1", "T2", "T3", "TN"), entries.map { trips[it.tripIdx] }.toSet())
    }

    @Test fun busRealtimeXml() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?>
            <estimacion><solicitud><parada>508</parada></solicitud>
            <bus><linea>93</linea><destino>Av. del Cid</destino><minutos>7 min.</minutos><horaLlegada>22:41</horaLlegada><error></error></bus>
            <bus><linea>4</linea><destino>Port</destino><minutos>Próximo</minutos><horaLlegada></horaLlegada><error></error></bus>
            <bus><linea>C1</linea><destino>x</destino><minutos></minutos><horaLlegada></horaLlegada><error>Sin estimación</error></bus>
            </estimacion>""".toByteArray(Charsets.ISO_8859_1)
        val r = BusRealtime.parse(xml)
        assertEquals(2, r.size)
        assertEquals(BusRealtime.Estimate("93", "Av. del Cid", 7, "22:41"), r[0])
        assertEquals(0, r[1].minutes)
    }

    @Test fun busRealtimeRejectsXxe() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
            <estimacion><bus><linea>&e;</linea><minutos>1 min.</minutos></bus></estimacion>""".toByteArray()
        assertTrue(BusRealtime.parse(evil).isEmpty())
        assertTrue(BusRealtime.parse("<html>oops".toByteArray()).isEmpty())
    }

    @Test fun railRealtime() {
        val tu = """{"header":{},"entity":[
            {"id":"a","tripUpdate":{"trip":{"tripId":"6182D80716C1","scheduleRelationship":"SCHEDULED"},"delay":240}},
            {"id":"b","tripUpdate":{"trip":{"tripId":"6182D80718C1","scheduleRelationship":"CANCELED"}}},
            {"id":"c","tripUpdate":{"trip":{"tripId":"6182D80720C2"},"stopTimeUpdate":[{"arrival":{"delay":-80040}}]}}]}"""
        val m = RailRealtime.parseTripUpdates(tu)
        assertEquals(240, m[RailRealtime.matchKey("4001J80716C1")]!!.delaySec)
        assertTrue(m[RailRealtime.matchKey("4001J80718C1")]!!.canceled)
        assertEquals(0, m[RailRealtime.matchKey("4001J80720C2")]!!.delaySec)
        val al = """{"entity":[{"id":"AV1","alert":{"activePeriod":[{"start":"100"}],
            "informedEntity":[{"routeId":"40T0001C1  "},{"routeId":"10T0001C1"}],
            "descriptionText":{"translation":[{"text":"Retrasos en C1. Disculpen.","language":"es"}]}}},
            {"id":"AV2","alert":{"informedEntity":[{"routeId":"10T0001C1"}],"descriptionText":{"translation":[{"text":"Madrid","language":"es"}]}}}]}"""
        val alerts = RailRealtime.parseAlerts(al, setOf("40T0001C1"), "ca")
        assertEquals(1, alerts.size)
        assertEquals(listOf("rail:40T0001C1"), alerts[0].routeKeys)
        assertEquals("Retrasos en C1", alerts[0].title)
        assertEquals(100L, alerts[0].startEpochSec)
    }

    @Test fun searchNormalization() {
        assertEquals("placa de l ajuntament", normalizeForSearch("Plaça de l'Ajuntament"))
        assertTrue(matchesQuery(normalizeForSearch("Àngel Guimerà"), normalizeForSearch("angel gui")))
        assertFalse(matchesQuery(normalizeForSearch("Àngel Guimerà"), normalizeForSearch("xativa")))
    }

    @Test fun polylineRoundTrip() {
        val pts = listOf(39.46992 to -0.37629, 39.47001 to -0.39002, 38.5 to -1.1)
        val back = Polyline.decode(Polyline.encode(pts))
        pts.zip(back).forEach { (a, b) -> assertEquals(a.first, b.first, 1e-5); assertEquals(a.second, b.second, 1e-5) }
    }

    @Test fun keys() {
        assertEquals(Network.BUS to "508", splitKey("bus:508"))
        assertNull(splitKey("nope"))
        assertNull(splitKey("zzz:1"))
    }
}
