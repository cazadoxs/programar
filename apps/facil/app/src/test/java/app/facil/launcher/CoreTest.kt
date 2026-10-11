package app.facil.launcher

import app.facil.launcher.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CoreTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun pinHashVerifies() {
        val salt = Pin.newSalt()
        val h = Pin.hash("1234", salt)
        assertTrue(Pin.verify("1234", salt, h))
        assertFalse(Pin.verify("1235", salt, h))
        assertFalse(Pin.verify("abcd", salt, h))
        assertFalse(Pin.verify("123", salt, h))
        assertNotEquals(Pin.hash("1234", Pin.newSalt()), h)
    }

    @Test fun throttleGrows() {
        assertEquals(0, Throttle.lockMillis(4))
        assertEquals(30_000, Throttle.lockMillis(5))
        assertEquals(60_000, Throttle.lockMillis(6))
        assertEquals(15 * 60_000L, Throttle.lockMillis(50))
        assertEquals(0, Throttle.remaining(5, 1000, 1000 + 31_000))
        assertEquals(29_000, Throttle.remaining(5, 1000, 2000))
    }

    @Test fun phoneCleaning() {
        assertEquals("612345678", Phone.clean(" 612 345-678 "))
        assertEquals("+34612345678", Phone.clean("+34 612 345 678"))
        assertEquals("+34612345678", Phone.clean("0034612345678"))
        assertEquals("", Phone.clean("abc"))
        assertEquals("34612345678", Phone.forWhatsApp("612345678"))
        assertEquals("34612345678", Phone.forWhatsApp("+34612345678"))
        assertEquals("612 345 678", Phone.pretty("612345678"))
        assertFalse(Phone.isValid("12"))
    }

    @Test fun codes() {
        val c = Codes.newCode()
        assertEquals(Codes.LENGTH, c.length)
        assertTrue(c.all { it in Codes.ALPHABET })
        assertEquals(c, Codes.normalize(Codes.qrPayload(c)))
        assertEquals(c, Codes.normalize(" " + Codes.pretty(c).lowercase() + " "))
        assertNull(Codes.normalize("SHORT"))
        assertNull(Codes.normalize("FACIL:!!!!!!!!!!"))
        assertEquals(200, (1..200).map { Codes.newCode() }.toSet().size)
    }

    @Test fun configRoundTrip() {
        val c = LauncherConfig.defaults().copy(
            ownerName = "Carmen", sosName = "María", sosPhone = "+34612345678", theme = LauncherConfig.THEME_NIGHT,
            textScale = 1.4f, reminders = listOf(Reminder("r1", "Pastilla", 9, 30, 0b0011111)), rev = 7,
        )
        val back = LauncherConfig.fromJsonString(c.toJson().toString())!!
        assertEquals(c.tiles, back.tiles)
        assertEquals("Carmen", back.ownerName)
        assertEquals(1.4f, back.textScale, 0.001f)
        assertEquals(c.reminders, back.reminders)
        assertEquals(7L, back.rev)
    }

    @Test fun configRejectsHostileInput() {
        val evil = JSONObject()
            .put("tiles", org.json.JSONArray()
                .put(JSONObject().put("id", "../x").put("type", "app").put("pkg", "javascript:alert(1)").put("label", "x"))
                .put(JSONObject().put("id", "ok").put("type", "shell").put("label", "rm"))
                .put(JSONObject().put("id", "p").put("type", "call").put("phone", "tel:*21*123#").put("label", "A\u0000B\n".repeat(50)).put("photo", "<script>")))
            .put("theme", "pink").put("textScale", 99).put("inactivityHours", 100000)
            .put("pinSalt", "x").put("pinHash", "y")
        val c = LauncherConfig.fromJson(evil)
        assertEquals(1, c.tiles.size)                      // app inválida y tipo desconocido descartados
        assertEquals("call", c.tiles[0].type)
        assertEquals("21123", c.tiles[0].phone)             // solo dígitos: sin códigos USSD
        assertEquals("", c.tiles[0].photo)
        assertTrue(c.tiles[0].label.length <= 30 && c.tiles[0].label.none { it.isISOControl() })
        assertEquals(LauncherConfig.THEME_CREAM, c.theme)
        assertEquals(1.4f, c.textScale, 0.001f)
        assertEquals(72, c.inactivityHours)
        assertFalse(c.hasPin)
        assertNull(LauncherConfig.fromJsonString("{not json"))
        assertNull(LauncherConfig.fromJsonString("x".repeat(LauncherConfig.MAX_JSON_CHARS + 1)))
    }

    @Test fun scheduleNextAndLast() {
        val r = Reminder("a", "x", 9, 0, 0b0000001) // solo lunes
        val tue = ms(2026, 10, 13, 12, 0)          // martes
        assertEquals(ms(2026, 10, 19, 9, 0), Schedule.next(r, tue, zone))
        assertEquals(ms(2026, 10, 12, 9, 0), Schedule.lastOccurrence(r, tue, zone))
        val daily = Reminder("b", "y", 20, 0)
        assertEquals(ms(2026, 10, 13, 20, 0), Schedule.next(daily, tue, zone))
        assertEquals(ms(2026, 10, 12, 20, 0), Schedule.lastOccurrence(daily, tue, zone))
    }

    @Test fun alerts() {
        val cfg = LauncherConfig.defaults().copy(
            reminders = listOf(Reminder("p", "Pastilla", 9, 0)), inactivityHours = 12)
        val now = ms(2026, 10, 13, 11, 0)
        val h = 3_600_000L
        // Todo bien
        var st = DeviceStatus(80, false, now - h, now - 60_000, remindersDone = mapOf("p" to ms(2026, 10, 13, 9, 5)))
        assertTrue(Alerts.evaluate("Carmen", st, cfg, now, zone).isEmpty())
        // Batería baja, sin cargar
        st = st.copy(battery = 10)
        assertEquals(AlertKind.LOW_BATTERY, Alerts.evaluate("Carmen", st, cfg, now, zone).single().kind)
        // Cargando: sin aviso
        assertTrue(Alerts.evaluate("Carmen", st.copy(charging = true), cfg, now, zone).isEmpty())
        // Inactividad
        st = DeviceStatus(80, false, now - 13 * h, now - 60_000, remindersDone = mapOf("p" to ms(2026, 10, 13, 9, 5)))
        assertEquals(AlertKind.INACTIVE, Alerts.evaluate("Carmen", st, cfg, now, zone).single().kind)
        // Pastilla sin marcar a las 11:00 (2 h tarde)
        st = DeviceStatus(80, false, now - h, now - 60_000)
        assertEquals(AlertKind.REMINDER_MISSED, Alerts.evaluate("Carmen", st, cfg, now, zone).single().kind)
        // Sin conexión > 24 h
        st = DeviceStatus(80, false, now - 30 * h, now - 30 * h, remindersDone = mapOf("p" to now))
        assertEquals(AlertKind.OFFLINE, Alerts.evaluate("Carmen", st, cfg, now, zone).single().kind)
        // SOS reciente
        st = DeviceStatus(80, false, now - h, now - 60_000, lastSos = now - 10 * 60_000, remindersDone = mapOf("p" to now))
        assertEquals(AlertKind.SOS, Alerts.evaluate("Carmen", st, cfg, now, zone).single().kind)
        // Nunca visto: nada
        assertTrue(Alerts.evaluate("Carmen", DeviceStatus(), cfg, now, zone).isEmpty())
    }
}
