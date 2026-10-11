package com.cazadoxs.filtro400.core

import java.time.Instant
import java.time.ZoneId

/** Por qué se tomó una decisión; se guarda en el historial y se muestra al usuario. */
enum class Reason {
    FILTER_OFF,
    ALLOWLIST,
    EXPECTED,
    BLOCKLIST,
    PREFIX_RULE,
    COMMERCIAL_400,
    PREMIUM_RATE,
    SHARED_COST,
    INTERNATIONAL,
    COMMUNITY,
    QUIET_HOURS,
    UNPARSEABLE,
    DEFAULT,
}

data class Decision(
    val action: CallAction,
    val reason: Reason,
    /** Etiqueta legible de la regla o entrada que decidió (puede estar vacía). */
    val detail: String = "",
)

/** Coincidencia en la lista de la comunidad. */
data class CommunityHit(val reports: Int, val category: SpamCategory)

/**
 * Motor de decisión. Es una función pura: sin E/S, sin Android, sin red.
 * El servicio de filtrado tiene 5 segundos para responder, así que todo lo que
 * necesita (configuración y lista de la comunidad) ya está en memoria.
 *
 * Orden de prioridad (gana la primera que coincide):
 *  1. Filtro apagado.
 *  2. Número en tu lista de permitidos.
 *  3. Número en tu lista negra.
 *  4. Llamada esperada activa.
 *  5. Tus reglas de prefijo (gana el prefijo más largo).
 *  6. Reglas por tipo de número (400, tarificación adicional, 902, extranjero).
 *  7. Lista de la comunidad (solo Premium).
 *  8. Horario de silencio.
 *  9. Dejar sonar.
 */
object CallFilter {

    fun decide(
        rawNumber: String?,
        config: FilterConfig,
        nowMs: Long,
        zone: ZoneId,
        community: ((String) -> CommunityHit?)? = null,
    ): Decision {
        if (!config.enabled) return Decision(CallAction.ALLOW, Reason.FILTER_OFF)

        val number = PhoneNumbers.normalize(rawNumber)
            ?: return quietOr(config, nowMs, zone, Decision(CallAction.ALLOW, Reason.UNPARSEABLE))
        val kind = PhoneNumbers.classify(number)

        config.allowed.firstOrNull { it.number == number }?.let {
            return Decision(CallAction.ALLOW, Reason.ALLOWLIST, it.label)
        }
        config.blocked.firstOrNull { it.number == number }?.let {
            return Decision(CallAction.BLOCK, Reason.BLOCKLIST, it.label)
        }

        val active = config.expected.filter { it.untilEpochMs > nowMs }
        active.firstOrNull { it.number != null && it.number == number }?.let {
            return Decision(CallAction.ALLOW, Reason.EXPECTED, it.label)
        }
        if (kind != NumberKind.COMMERCIAL_400) {
            active.firstOrNull { it.number == null }?.let {
                return Decision(CallAction.ALLOW, Reason.EXPECTED, it.label)
            }
        }

        config.prefixRules
            .filter { number.startsWith(it.prefix) }
            .maxByOrNull { it.prefix.length }
            ?.let { return Decision(it.action, Reason.PREFIX_RULE, it.label.ifBlank { it.prefix }) }

        val byKind = when (kind) {
            NumberKind.COMMERCIAL_400 -> Decision(config.commercial400, Reason.COMMERCIAL_400)
            NumberKind.PREMIUM_RATE -> Decision(config.premiumRate, Reason.PREMIUM_RATE)
            NumberKind.SHARED_COST -> Decision(config.sharedCost, Reason.SHARED_COST)
            NumberKind.INTERNATIONAL -> Decision(config.international, Reason.INTERNATIONAL)
            else -> null
        }
        if (byKind != null && byKind.action != CallAction.ALLOW) return byKind

        if (community != null && config.community != CallAction.ALLOW) {
            community(number)?.let {
                return Decision(config.community, Reason.COMMUNITY, it.category.label)
            }
        }

        return quietOr(config, nowMs, zone, Decision(CallAction.ALLOW, Reason.DEFAULT))
    }

    private fun quietOr(config: FilterConfig, nowMs: Long, zone: ZoneId, fallback: Decision): Decision {
        val q = config.quietHours
        if (q.enabled && q.action != CallAction.ALLOW && isInQuietHours(q, nowMs, zone)) {
            return Decision(q.action, Reason.QUIET_HOURS)
        }
        return fallback
    }

    /**
     * La franja puede cruzar la medianoche (22:00 -> 08:00). En ese caso el día que
     * cuenta es el día en que empezó la franja: el lunes a las 02:00 pertenece a la
     * franja del domingo.
     */
    fun isInQuietHours(q: QuietHours, nowMs: Long, zone: ZoneId): Boolean {
        if (q.startMinute == q.endMinute) return false
        val t = Instant.ofEpochMilli(nowMs).atZone(zone)
        val minute = t.hour * 60 + t.minute
        val today = t.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        fun dayOk(d: Int) = q.days.isEmpty() || d in q.days
        return if (q.startMinute < q.endMinute) {
            minute >= q.startMinute && minute < q.endMinute && dayOk(today)
        } else {
            (minute >= q.startMinute && dayOk(today)) || (minute < q.endMinute && dayOk(yesterday))
        }
    }
}
