package vlc.transit.core.realtime

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses the arrival-estimate XML that València's municipal bus operator
 * serves for a stop:
 *
 *   <estimacion><solicitud>…</solicitud>
 *     <bus><linea>4</linea><destino>…</destino><minutos>5 min.</minutos>
 *          <horaLlegada>22:41</horaLlegada><error/></bus>…
 *   </estimacion>
 *
 * Unknown structure or an <error> yields an empty list; callers then fall back
 * to the timetable.
 */
object BusRealtime {

    data class Estimate(val line: String, val destination: String, val minutes: Int?, val clock: String?)

    fun parse(body: ByteArray): List<Estimate> {
        if (body.isEmpty()) return emptyList()
        val doc = try {
            safeFactory().newDocumentBuilder().parse(ByteArrayInputStream(body))
        } catch (_: Exception) {
            return emptyList()
        }
        val buses = doc.getElementsByTagName("bus")
        val out = ArrayList<Estimate>()
        for (i in 0 until buses.length) {
            val b = buses.item(i) as? Element ?: continue
            val line = text(b, "linea") ?: continue
            val err = text(b, "error")
            if (!err.isNullOrBlank() && err != "0") continue
            val minutesText = text(b, "minutos")
            val clock = text(b, "horaLlegada")?.takeIf { CLOCK.matches(it) }
            val minutes = parseMinutes(minutesText)
            if (minutes == null && clock == null) continue
            out += Estimate(line, text(b, "destino").orEmpty(), minutes, clock)
        }
        return out
    }

    private val CLOCK = Regex("\\d{1,2}:\\d{2}")
    private val DIGITS = Regex("(\\d+)")

    /** "5 min." -> 5, "Próximo"/"En parada" -> 0, "" -> null. */
    fun parseMinutes(s: String?): Int? {
        val t = s?.trim()?.lowercase() ?: return null
        if (t.isEmpty()) return null
        if (t.startsWith("pr") || t.contains("parada") || t.startsWith("lleg") || t.startsWith("arr")) return 0
        return DIGITS.find(t)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..240 }
    }

    private fun text(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent?.trim()?.replace(Regex("\\s+"), " ")
    }

    /** XXE-hardened parser: no DTDs, no external entities, no XInclude. */
    private fun safeFactory(): DocumentBuilderFactory {
        val f = DocumentBuilderFactory.newInstance()
        fun feature(name: String, on: Boolean) = try { f.setFeature(name, on) } catch (_: Exception) {}
        feature("http://apache.org/xml/features/disallow-doctype-decl", true)
        feature("http://xml.org/sax/features/external-general-entities", false)
        feature("http://xml.org/sax/features/external-parameter-entities", false)
        feature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        feature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        try { f.isXIncludeAware = false } catch (_: Exception) {}
        f.isExpandEntityReferences = false
        f.isNamespaceAware = false
        return f
    }
}
