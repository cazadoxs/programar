package vlc.transit.core.realtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vlc.transit.core.Network
import vlc.transit.core.ServiceAlert

/**
 * Curated notices file (avisos/avisos.json in the repository). Lets the
 * maintainer publish diversions, strikes and works for bus and metro, which
 * have no machine-readable alert feed, without shipping an app update.
 */
object Notices {
    @Serializable
    data class File(val version: Int = 1, val notices: List<Notice> = emptyList())

    @Serializable
    data class Notice(
        val id: String,
        val network: String,
        val title: Map<String, String>,
        val body: Map<String, String> = emptyMap(),
        val lines: List<String> = emptyList(),
        val stops: List<String> = emptyList(),
        val start: Long? = null,
        val end: Long? = null,
        val url: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String, lang: String, nowEpochSec: Long): List<ServiceAlert> =
        json.decodeFromString(File.serializer(), body).notices.mapNotNull { n ->
            val net = Network.fromPrefix(n.network) ?: return@mapNotNull null
            if (n.end != null && n.end < nowEpochSec) return@mapNotNull null
            val title = n.title[lang] ?: n.title["es"] ?: n.title.values.firstOrNull() ?: return@mapNotNull null
            ServiceAlert(
                id = "notice:${n.id}",
                network = net,
                title = title.take(140),
                body = (n.body[lang] ?: n.body["es"] ?: n.body.values.firstOrNull() ?: "").take(2000),
                routeKeys = n.lines.map { "${net.prefix}:line:$it" },
                stopKeys = n.stops.map { "${net.prefix}:$it" },
                startEpochSec = n.start,
                endEpochSec = n.end,
                url = n.url?.takeIf { it.startsWith("https://") },
            )
        }
}
