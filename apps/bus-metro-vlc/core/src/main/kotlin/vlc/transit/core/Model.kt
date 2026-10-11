package vlc.transit.core

/** The networks the app knows about. [prefix] namespaces ids across feeds. */
enum class Network(val prefix: String) {
    BUS("bus"),
    METRO("metro"),
    RAIL("rail"),
    BIKE("bike");

    companion object {
        fun fromPrefix(p: String): Network? = entries.firstOrNull { it.prefix == p }
    }
}

/** "bus:508" -> (BUS, "508"). Ids from the database always carry the prefix. */
fun splitKey(key: String): Pair<Network, String>? {
    val i = key.indexOf(':')
    if (i <= 0) return null
    val n = Network.fromPrefix(key.substring(0, i)) ?: return null
    return n to key.substring(i + 1)
}

fun key(network: Network, rawId: String) = "${network.prefix}:$rawId"

/** One upcoming vehicle at a stop, after merging the timetable with live data. */
data class Arrival(
    val routeKey: String,
    val line: String,
    val headsign: String,
    /** Epoch seconds of the expected departure, when known. */
    val epochSec: Long?,
    /** Whole minutes from "now" (0 = arriving). */
    val minutes: Int,
    /** True when the time comes from a live source rather than the timetable. */
    val realtime: Boolean,
    val delaySec: Int? = null,
    val canceled: Boolean = false,
)

/** A service alert, already narrowed to this app's networks. */
data class ServiceAlert(
    val id: String,
    val network: Network,
    val title: String,
    val body: String,
    val routeKeys: List<String>,
    val stopKeys: List<String>,
    val startEpochSec: Long?,
    val endEpochSec: Long?,
    val url: String? = null,
)

data class BikeStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val bikes: Int,
    val docks: Int,
    val total: Int,
    val open: Boolean,
    val updatedEpochSec: Long?,
)
