package vlc.transit.core.feeds

import vlc.transit.core.Network
import vlc.transit.core.gtfs.CompileOptions

/**
 * Where each network's open data lives. Every URL is HTTPS; a mirror is
 * tried when the operator's own server is down or blocks the request.
 */
data class FeedSource(val network: Network, val urls: List<String>, val options: CompileOptions, val license: String)

object Feeds {
    val BUS = FeedSource(
        Network.BUS,
        listOf(
            "https://opendata.vlci.valencia.es/dataset/ab058cf8-ad3e-4d9c-ac89-0c6367ecf351/resource/c81b69e6-c082-44dc-acc6-66fc417b4e66/download/google_transit.zip",
            "https://files.mobilitydatabase.org/mdb-795/latest.zip",
        ),
        CompileOptions(Network.BUS),
        "Ajuntament de València — Datos abiertos (CC BY 4.0)",
    )
    val METRO = FeedSource(
        Network.METRO,
        listOf(
            "https://www.metrovalencia.es/google_transit_feed/google_transit.zip",
            "https://files.mobilitydatabase.org/mdb-1054/latest.zip",
        ),
        CompileOptions(Network.METRO),
        "FGV — horarios GTFS publicados por el operador",
    )
    val RAIL = FeedSource(
        Network.RAIL,
        listOf(
            "https://ssl.renfe.com/ftransit/Fichero_CER_FOMENTO/fomento_transit.zip",
            "https://files.mobilitydatabase.org/mdb-2653/latest.zip",
        ),
        // Núcleo de València only: its route ids start with "40T".
        CompileOptions(Network.RAIL, keepTripIds = true, routeFilter = { it.startsWith("40T") }),
        "Renfe Data — datos abiertos (CC BY 4.0)",
    )
    val ALL = listOf(BUS, METRO, RAIL)

    const val RAIL_TRIP_UPDATES = "https://gtfsrt.renfe.com/trip_updates.json"
    const val RAIL_ALERTS = "https://gtfsrt.renfe.com/alerts.json"

    /** Live arrival estimates for a bus stop (stop code appended). */
    const val BUS_ESTIMATES = "https://www.emtvalencia.es/EMT/mapfunctions/MapUtilsPetitions.php?sec=getSAE&adaptados=false&parada="

    /** Valenbisi availability from València's open-data portal (CKAN datastore). */
    const val BIKE_CKAN = "https://opendata.vlci.valencia.es/api/3/action/datastore_search?resource_id=6afb29b7-43a6-4887-859d-c7075c69c200&limit=500"
    /** JCDecaux's API, used instead when a developer key is configured. */
    const val BIKE_JCDECAUX = "https://api.jcdecaux.com/vls/v3/stations?contract=valence&apiKey="

    /** Hand-curated notices (works, strikes, diversions) published from the app's repository. */
    const val NOTICES = "https://raw.githubusercontent.com/cazadoxs/programar/HEAD/apps/bus-metro-vlc/avisos/avisos.json"

    /** Hosts the app may talk to; anything else is refused (see the network security config too). */
    val ALLOWED_HOSTS = setOf(
        "opendata.vlci.valencia.es", "files.mobilitydatabase.org", "www.metrovalencia.es",
        "ssl.renfe.com", "gtfsrt.renfe.com", "www.emtvalencia.es", "api.jcdecaux.com",
        "raw.githubusercontent.com", "tiles.openfreemap.org",
    )
}
