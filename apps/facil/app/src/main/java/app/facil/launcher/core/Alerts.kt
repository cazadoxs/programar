package app.facil.launcher.core

/**
 * Estado que el móvil de la persona mayor comparte con su familia (si lo
 * permite). Nada de ubicación, mensajes ni llamadas: solo lo necesario para
 * saber que todo va bien.
 */
data class DeviceStatus(
    val battery: Int = -1,
    val charging: Boolean = false,
    val lastActive: Long = 0,
    val lastSeen: Long = 0,
    /** Última vez que se pulsó el botón de ayuda. */
    val lastSos: Long = 0,
    /** id de recordatorio -> momento en que se marcó como hecho. */
    val remindersDone: Map<String, Long> = emptyMap(),
)

enum class AlertKind { SOS, LOW_BATTERY, INACTIVE, REMINDER_MISSED, OFFLINE }

data class Alert(val kind: AlertKind, val key: String, val text: String)

object Alerts {
    const val LOW_BATTERY = 15
    const val OFFLINE_HOURS = 24
    const val REMINDER_GRACE_MIN = 60

    fun evaluate(
        name: String,
        status: DeviceStatus,
        config: LauncherConfig,
        now: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<Alert> {
        val out = mutableListOf<Alert>()
        if (status.lastSeen == 0L) return out
        val hour = 3_600_000L
        if (status.lastSos > 0 && now - status.lastSos < 2 * hour) {
            out += Alert(AlertKind.SOS, "sos_${status.lastSos}", "$name ha pulsado el botón de AYUDA")
        }
        if (status.battery in 0..LOW_BATTERY && !status.charging) {
            out += Alert(AlertKind.LOW_BATTERY, "bat", "A $name le queda un ${status.battery} % de batería")
        }
        if (now - status.lastSeen > OFFLINE_HOURS * hour) {
            out += Alert(AlertKind.OFFLINE, "off", "El móvil de $name lleva más de $OFFLINE_HOURS h sin conexión")
        } else if (status.lastActive > 0 && now - status.lastActive > config.inactivityHours * hour) {
            out += Alert(AlertKind.INACTIVE, "idle", "$name no ha usado el móvil en más de ${config.inactivityHours} h")
        }
        for (r in config.reminders) {
            val due = Schedule.lastOccurrence(r, now, zone) ?: continue
            val late = now - due
            // Solo avisamos dentro de la ventana de unas horas tras la hora prevista.
            if (late < REMINDER_GRACE_MIN * 60_000L || late > 6 * hour) continue
            val done = status.remindersDone[r.id] ?: 0
            if (done < due - 30 * 60_000L) {
                out += Alert(AlertKind.REMINDER_MISSED, "rem_${r.id}_$due", "$name no ha marcado «${r.label}» de las ${r.timeText()}")
            }
        }
        return out
    }
}
