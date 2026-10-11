package vlc.transit.core.realtime

import kotlinx.serialization.json.*
import vlc.transit.core.Network
import vlc.transit.core.ServiceAlert
import vlc.transit.core.key

/**
 * Parses Renfe's GTFS-Realtime feeds in their JSON rendering
 * (https://gtfsrt.renfe.com/trip_updates.json and alerts.json).
 */
object RailRealtime {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class TripDelay(val delaySec: Int, val canceled: Boolean)

    /**
     * Renfe's live feed and its timetable use different calendar prefixes for
     * the same train ("6182D80716C1" live vs "4001J80716C1" scheduled): the
     * first five characters encode the calendar, the rest is train number and
     * line. Both sides are matched on that remainder.
     */
    fun matchKey(tripId: String): String = tripId.trim().let { if (it.length > 7) it.substring(5) else it }

    fun parseTripUpdates(body: String): Map<String, TripDelay> {
        val root = json.parseToJsonElement(body).jsonObject
        val out = HashMap<String, TripDelay>()
        for (e in root["entity"]?.jsonArray.orEmpty()) {
            val tu = e.jsonObject["tripUpdate"]?.jsonObject ?: continue
            val trip = tu["trip"]?.jsonObject ?: continue
            val id = trip["tripId"]?.jsonPrimitive?.contentOrNull ?: continue
            val canceled = trip["scheduleRelationship"]?.jsonPrimitive?.contentOrNull == "CANCELED"
            val delay = tu["delay"]?.jsonPrimitive?.intOrNull
                ?: tu["stopTimeUpdate"]?.jsonArray?.firstOrNull()?.jsonObject?.let { stu ->
                    (stu["departure"] ?: stu["arrival"])?.jsonObject?.get("delay")?.jsonPrimitive?.intOrNull
                } ?: 0
            // Renfe sometimes publishes whole-day offsets (-80040 s); those are
            // not delays of today's train, so anything beyond ±3 h is ignored.
            out[matchKey(id)] = TripDelay(if (delay in -10800..10800) delay else 0, canceled)
        }
        return out
    }

    /**
     * Keeps only alerts that touch [knownRouteIds] (raw Renfe route ids of the
     * València núcleo), so Madrid's notices never show up.
     */
    fun parseAlerts(body: String, knownRouteIds: Set<String>, preferLang: String): List<ServiceAlert> {
        val root = json.parseToJsonElement(body).jsonObject
        val out = ArrayList<ServiceAlert>()
        for (e in root["entity"]?.jsonArray.orEmpty()) {
            val obj = e.jsonObject
            val alert = obj["alert"]?.jsonObject ?: continue
            val routes = alert["informedEntity"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["routeId"]?.jsonPrimitive?.contentOrNull?.trim() }
                .filter { it in knownRouteIds }.distinct()
            if (routes.isEmpty()) continue
            val period = alert["activePeriod"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = pick(alert["descriptionText"], preferLang) ?: continue
            val header = pick(alert["headerText"], preferLang)
            out += ServiceAlert(
                id = "rail:" + (obj["id"]?.jsonPrimitive?.contentOrNull ?: text.hashCode().toString()),
                network = Network.RAIL,
                title = header ?: text.substringBefore('.').take(90),
                body = text,
                routeKeys = routes.map { key(Network.RAIL, it) },
                stopKeys = emptyList(),
                startEpochSec = period?.get("start")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                endEpochSec = period?.get("end")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )
        }
        return out
    }

    private fun pick(el: JsonElement?, lang: String): String? {
        val tr = el?.jsonObject?.get("translation")?.jsonArray ?: return null
        val all = tr.mapNotNull { t ->
            val o = t.jsonObject
            val txt = o["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            (o["language"]?.jsonPrimitive?.contentOrNull ?: "") to txt
        }
        return (all.firstOrNull { it.first == lang } ?: all.firstOrNull { it.first == "es" } ?: all.firstOrNull())?.second
    }
}
