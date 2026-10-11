package com.cazadoxs.filtro400.core

import kotlinx.serialization.Serializable

/** Qué hacer con una llamada entrante. */
@Serializable
enum class CallAction {
    /** Suena con normalidad. */
    ALLOW,
    /** Entra pero el teléfono no suena ni vibra; queda como llamada perdida. */
    SILENCE,
    /** Se cuelga antes de sonar. */
    BLOCK,
}

/** Regla de prefijo creada por el usuario ("+34902" -> BLOCK). */
@Serializable
data class PrefixRule(
    val id: String,
    val prefix: String,
    val action: CallAction,
    val label: String = "",
)

/** Número concreto en la lista negra o en la lista de permitidos. */
@Serializable
data class NumberEntry(
    val number: String,
    val label: String = "",
    val addedAt: Long = 0,
)

/**
 * "Llamada esperada": deja pasar llamadas de números desconocidos durante un
 * tiempo (el repartidor, el centro de salud, la inmobiliaria...).
 *
 * Si [number] es null, vale para cualquier número desconocido salvo los que
 * estén en tu lista negra o empiecen por 400: un repartidor o un médico nunca
 * llaman desde el rango comercial.
 */
@Serializable
data class ExpectedCall(
    val id: String,
    val label: String,
    val number: String? = null,
    val untilEpochMs: Long,
)

/** Franja en la que las llamadas de desconocidos no suenan. Minutos desde las 00:00. */
@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 8 * 60,
    /** 1 = lunes ... 7 = domingo (java.time.DayOfWeek). Vacío = todos los días. */
    val days: Set<Int> = emptySet(),
    val action: CallAction = CallAction.SILENCE,
)

@Serializable
data class FilterConfig(
    val enabled: Boolean = true,
    val commercial400: CallAction = CallAction.BLOCK,
    val premiumRate: CallAction = CallAction.BLOCK,
    val sharedCost: CallAction = CallAction.ALLOW,
    val international: CallAction = CallAction.ALLOW,
    /** Números con suficientes denuncias en la lista de la comunidad (Premium). */
    val community: CallAction = CallAction.SILENCE,
    val prefixRules: List<PrefixRule> = emptyList(),
    val blocked: List<NumberEntry> = emptyList(),
    val allowed: List<NumberEntry> = emptyList(),
    val expected: List<ExpectedCall> = emptyList(),
    val quietHours: QuietHours = QuietHours(),
    /** No dejar rastro de las llamadas bloqueadas en el registro del teléfono. */
    val skipCallLog: Boolean = false,
    /** Aviso discreto cada vez que se corta o silencia una llamada. */
    val notifyOnBlock: Boolean = true,
    /** Días que se guarda el historial propio de Filtro 400. */
    val historyDays: Int = 30,
) {
    fun withoutExpired(nowMs: Long): FilterConfig =
        copy(expected = expected.filter { it.untilEpochMs > nowMs })
}
