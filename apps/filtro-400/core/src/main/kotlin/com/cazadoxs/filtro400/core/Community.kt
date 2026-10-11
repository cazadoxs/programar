package com.cazadoxs.filtro400.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class SpamCategory(val label: String) {
    @SerialName("comercial") SALES("Comercial"),
    @SerialName("energia") ENERGY("Luz y gas"),
    @SerialName("telefonia") TELECOM("Telefonía"),
    @SerialName("finanzas") FINANCE("Préstamos y finanzas"),
    @SerialName("encuesta") SURVEY("Encuesta"),
    @SerialName("robot") ROBOCALL("Robot / llamada muda"),
    @SerialName("estafa") SCAM("Posible estafa"),
    @SerialName("otro") OTHER("Otro"),
}

/** Entrada publicada por el servidor: solo números con varias denuncias independientes. */
@Serializable
data class CommunityEntry(
    @SerialName("n") val number: String,
    @SerialName("r") val reports: Int,
    @SerialName("c") val category: SpamCategory,
)

@Serializable
data class CommunityList(
    val version: Long,
    val generatedAt: Long,
    val minReports: Int,
    val entries: List<CommunityEntry>,
)

/**
 * Índice en memoria de la lista de la comunidad. La consulta es local:
 * los números que te llaman nunca salen del teléfono.
 */
class CommunityIndex private constructor(
    private val byNumber: Map<String, CommunityEntry>,
    val version: Long,
    val size: Int,
) {
    fun lookup(number: String): CommunityHit? =
        byNumber[number]?.let { CommunityHit(it.reports, it.category) }

    companion object {
        val EMPTY = CommunityIndex(emptyMap(), 0, 0)

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parsea la lista descargada descartando cualquier entrada mal formada o que
         * no sea un número E.164: el servidor no es de fiar a ciegas.
         */
        fun parse(text: String): CommunityIndex {
            val list = json.decodeFromString(CommunityList.serializer(), text)
            val map = HashMap<String, CommunityEntry>(list.entries.size * 2)
            for (e in list.entries) {
                val n = PhoneNumbers.normalize(e.number) ?: continue
                if (n != e.number || !n.startsWith("+")) continue
                if (e.reports < list.minReports.coerceAtLeast(1)) continue
                map[n] = e
            }
            return CommunityIndex(map, list.version, map.size)
        }
    }
}

/** Denuncia anónima que envía la app: número que llamó + categoría. Nada más. */
@Serializable
data class SpamReport(
    val number: String,
    val category: SpamCategory,
    /** Identificador aleatorio de la instalación, solo para evitar denuncias duplicadas. */
    val reporter: String,
) {
    companion object {
        private val REPORTER = Regex("^[A-Za-z0-9_-]{16,64}$")

        /** Solo se pueden denunciar números completos (no cortos ni ilegibles). */
        fun create(rawNumber: String, category: SpamCategory, reporter: String): SpamReport? {
            val n = PhoneNumbers.normalize(rawNumber) ?: return null
            if (!n.startsWith("+")) return null
            if (!REPORTER.matches(reporter)) return null
            // 112, 016 y similares nunca llegan aquí (son cortos); los 800/900 de
            // atención al cliente sí se pueden denunciar: a veces se usan para vender.
            return SpamReport(n, category, reporter)
        }
    }
}
