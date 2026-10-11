package vlc.transit.core.realtime

import kotlinx.serialization.json.*
import vlc.transit.core.BikeStation

/** Valenbisi availability, from the city's CKAN datastore or from JCDecaux. */
object BikeRealtime {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): List<BikeStation> {
        val root = json.parseToJsonElement(body)
        val records: List<JsonObject> = when {
            root is JsonArray -> root.map { it.jsonObject }                                  // JCDecaux v3
            root is JsonObject && root["result"] != null ->
                root["result"]!!.jsonObject["records"]?.jsonArray?.map { it.jsonObject }.orEmpty() // CKAN
            root is JsonObject && root["features"] != null ->
                root["features"]!!.jsonArray.map { f ->                                      // GeoJSON
                    val o = f.jsonObject
                    val props = o["properties"]?.jsonObject ?: JsonObject(emptyMap())
                    val coords = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                    JsonObject(props + mapOf(
                        "_lon" to (coords?.getOrNull(0) ?: JsonNull),
                        "_lat" to (coords?.getOrNull(1) ?: JsonNull),
                    ))
                }
            else -> emptyList()
        }
        return records.mapNotNull(::station)
    }

    private fun station(o: JsonObject): BikeStation? {
        val id = str(o, "number", "Number", "numero", "id") ?: return null
        val name = str(o, "address", "Address", "name", "direccion") ?: "Valenbisi $id"
        var lat = num(o, "_lat", "lat", "latitude")
        var lon = num(o, "_lon", "lon", "lng", "longitude")
        o["position"]?.let { p ->
            (p as? JsonObject)?.let { lat = lat ?: num(it, "latitude", "lat"); lon = lon ?: num(it, "longitude", "lng") }
        }
        (o["geo_point_2d"] ?: o["geo_point"])?.let { g ->
            when (g) {
                is JsonObject -> { lat = lat ?: num(g, "lat"); lon = lon ?: num(g, "lon") }
                is JsonArray -> { lat = lat ?: g.getOrNull(0)?.jsonPrimitive?.doubleOrNull; lon = lon ?: g.getOrNull(1)?.jsonPrimitive?.doubleOrNull }
                is JsonPrimitive -> g.contentOrNull?.split(',')?.let { p -> lat = lat ?: p.getOrNull(0)?.trim()?.toDoubleOrNull(); lon = lon ?: p.getOrNull(1)?.trim()?.toDoubleOrNull() }
                else -> {}
            }
        }
        val la = lat ?: return null
        val lo = lon ?: return null
        val total = o["totalStands"]?.jsonObject
        val bikes = num(o, "available", "available_bikes", "bicis_disponibles")?.toInt()
            ?: total?.get("availabilities")?.jsonObject?.let { num(it, "bikes") }?.toInt() ?: 0
        val docks = num(o, "free", "available_bike_stands", "espacios_libres")?.toInt()
            ?: total?.get("availabilities")?.jsonObject?.let { num(it, "stands") }?.toInt() ?: 0
        val cap = num(o, "total", "bike_stands")?.toInt() ?: total?.let { num(it, "capacity") }?.toInt() ?: (bikes + docks)
        val openTxt = str(o, "open", "status")?.uppercase()
        val open = openTxt == null || openTxt == "T" || openTxt == "TRUE" || openTxt == "OPEN" || openTxt == "1"
        val upd = num(o, "update_jcd", "last_update", "lastUpdate")?.toLong()?.let { if (it > 10_000_000_000L) it / 1000 else it }
        return BikeStation(id, name.trim(), la, lo, bikes, docks, cap, open, upd)
    }

    private fun str(o: JsonObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun num(o: JsonObject, vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
        (o[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull() }
    }
}
