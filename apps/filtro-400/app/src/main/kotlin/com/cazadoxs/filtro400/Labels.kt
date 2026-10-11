package com.cazadoxs.filtro400

import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.Reason

object Labels {
    fun action(a: CallAction) = when (a) {
        CallAction.ALLOW -> "Dejar sonar"
        CallAction.SILENCE -> "Silenciar"
        CallAction.BLOCK -> "Bloquear"
    }

    fun actionPast(a: CallAction) = when (a) {
        CallAction.ALLOW -> "Ha sonado"
        CallAction.SILENCE -> "Silenciada"
        CallAction.BLOCK -> "Cortada"
    }

    fun reason(r: Reason, detail: String = ""): String {
        val base = when (r) {
            Reason.FILTER_OFF -> "Filtro apagado"
            Reason.ALLOWLIST -> "En tus permitidos"
            Reason.EXPECTED -> "Llamada esperada"
            Reason.BLOCKLIST -> "En tu lista negra"
            Reason.PREFIX_RULE -> "Tu regla de prefijo"
            Reason.COMMERCIAL_400 -> "Comercial (400)"
            Reason.PREMIUM_RATE -> "Tarificación adicional"
            Reason.SHARED_COST -> "Coste compartido (901/902)"
            Reason.INTERNATIONAL -> "Número extranjero"
            Reason.COMMUNITY -> "Denunciado por la comunidad"
            Reason.QUIET_HOURS -> "Horario de silencio"
            Reason.UNPARSEABLE -> "Número no reconocido"
            Reason.DEFAULT -> "Ninguna regla"
        }
        return if (detail.isNotBlank() && detail != base) "$base · $detail" else base
    }
}
