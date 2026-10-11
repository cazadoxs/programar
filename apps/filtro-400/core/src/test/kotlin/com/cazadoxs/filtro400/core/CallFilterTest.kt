package com.cazadoxs.filtro400.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PhoneNumbersTest {

    @Test fun normalizesSpanishFormats() {
        val expected = "+34400123456"
        listOf(
            "400123456", "400 12 34 56", "+34 400 123 456", "0034400123456",
            "34400123456", "tel:+34400123456", "tel:%2B34400123456", "(+34) 400-123-456",
        ).forEach { assertEquals(it, expected, PhoneNumbers.normalize(it)) }
    }

    @Test fun keepsInternationalAndShort() {
        assertEquals("+442071234567", PhoneNumbers.normalize("+44 20 7123 4567"))
        assertEquals("+442071234567", PhoneNumbers.normalize("0044 20 7123 4567"))
        assertEquals("1004", PhoneNumbers.normalize("1004"))
        assertNull(PhoneNumbers.normalize(""))
        assertNull(PhoneNumbers.normalize("abc"))
        assertNull(PhoneNumbers.normalize("12"))
        assertNull(PhoneNumbers.normalize(null))
    }

    @Test fun classifiesRanges() {
        assertEquals(NumberKind.COMMERCIAL_400, PhoneNumbers.classify("+34400123456"))
        assertEquals(NumberKind.PREMIUM_RATE, PhoneNumbers.classify("+34806123456"))
        assertEquals(NumberKind.PREMIUM_RATE, PhoneNumbers.classify("+34905123456"))
        assertEquals(NumberKind.SHARED_COST, PhoneNumbers.classify("+34902123456"))
        assertEquals(NumberKind.FREEPHONE, PhoneNumbers.classify("+34900123456"))
        assertEquals(NumberKind.MOBILE, PhoneNumbers.classify("+34612345678"))
        assertEquals(NumberKind.MOBILE, PhoneNumbers.classify("+34712345678"))
        assertEquals(NumberKind.PERSONAL, PhoneNumbers.classify("+34702345678"))
        assertEquals(NumberKind.LANDLINE, PhoneNumbers.classify("+34912345678"))
        assertEquals(NumberKind.INTERNATIONAL, PhoneNumbers.classify("+442071234567"))
        assertEquals(NumberKind.SHORT, PhoneNumbers.classify("1004"))
    }

    @Test fun formatsAndNormalizesPrefixes() {
        assertEquals("400 123 456", PhoneNumbers.format("+34400123456"))
        assertEquals("+34902", PhoneNumbers.normalizePrefix("902"))
        assertEquals("+44", PhoneNumbers.normalizePrefix("0044"))
        assertEquals("+34400", PhoneNumbers.normalizePrefix("+34 400"))
        assertNull(PhoneNumbers.normalizePrefix("12"))
    }
}

class CallFilterTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // Miércoles 14 de octubre de 2026, 12:00
    private val noon = at(2026, 10, 14, 12)
    private val base = FilterConfig()

    private fun decide(n: String, c: FilterConfig = base, now: Long = noon, community: ((String) -> CommunityHit?)? = null) =
        CallFilter.decide(n, c, now, zone, community)

    @Test fun blocks400ByDefault() {
        val d = decide("400 123 456")
        assertEquals(CallAction.BLOCK, d.action)
        assertEquals(Reason.COMMERCIAL_400, d.reason)
    }

    @Test fun letsNormalCallsRing() {
        assertEquals(CallAction.ALLOW, decide("612345678").action)
        assertEquals(Reason.DEFAULT, decide("612345678").reason)
    }

    @Test fun filterOffAllowsEverything() {
        assertEquals(CallAction.ALLOW, decide("400123456", base.copy(enabled = false)).action)
    }

    @Test fun allowlistBeats400() {
        val c = base.copy(allowed = listOf(NumberEntry("+34400123456", "Mi banco")))
        val d = decide("400123456", c)
        assertEquals(CallAction.ALLOW, d.action)
        assertEquals("Mi banco", d.detail)
    }

    @Test fun blocklistBeatsExpectedAny() {
        val c = base.copy(
            blocked = listOf(NumberEntry("+34612345678")),
            expected = listOf(ExpectedCall("1", "Repartidor", null, noon + 3_600_000)),
        )
        assertEquals(CallAction.BLOCK, decide("612345678", c).action)
    }

    @Test fun expectedAnyOverridesRulesExcept400() {
        val c = base.copy(
            international = CallAction.BLOCK,
            expected = listOf(ExpectedCall("1", "Paquete", null, noon + 3_600_000)),
        )
        assertEquals(Reason.EXPECTED, decide("+442071234567", c).reason)
        assertEquals(CallAction.BLOCK, decide("400123456", c).action)
    }

    @Test fun expectedForSpecificNumberOverrides400() {
        val c = base.copy(expected = listOf(ExpectedCall("1", "Oferta pedida", "+34400123456", noon + 1000)))
        assertEquals(CallAction.ALLOW, decide("400123456", c).action)
    }

    @Test fun expiredExpectedIsIgnored() {
        val c = base.copy(
            international = CallAction.BLOCK,
            expected = listOf(ExpectedCall("1", "Paquete", null, noon - 1)),
        )
        assertEquals(CallAction.BLOCK, decide("+442071234567", c).action)
        assertTrue(c.withoutExpired(noon).expected.isEmpty())
    }

    @Test fun longestPrefixWins() {
        val c = base.copy(
            prefixRules = listOf(
                PrefixRule("a", "+3491", CallAction.SILENCE, "Madrid"),
                PrefixRule("b", "+34912", CallAction.ALLOW, "Oficina"),
            ),
        )
        assertEquals(CallAction.ALLOW, decide("912345678", c).action)
        assertEquals(CallAction.SILENCE, decide("913345678", c).action)
    }

    @Test fun prefixRuleCanAllow400Subrange() {
        val c = base.copy(prefixRules = listOf(PrefixRule("a", "+34400999", CallAction.ALLOW)))
        assertEquals(CallAction.ALLOW, decide("400999123", c).action)
        assertEquals(CallAction.BLOCK, decide("400111123", c).action)
    }

    @Test fun communityOnlyWhenProvided() {
        val hit: (String) -> CommunityHit? = { if (it == "+34612345678") CommunityHit(9, SpamCategory.ENERGY) else null }
        assertEquals(CallAction.ALLOW, decide("612345678").action)
        val d = decide("612345678", community = hit)
        assertEquals(CallAction.SILENCE, d.action)
        assertEquals(Reason.COMMUNITY, d.reason)
        assertEquals(CallAction.ALLOW, decide("612345678", base.copy(community = CallAction.ALLOW), community = hit).action)
    }

    @Test fun quietHoursAcrossMidnight() {
        val q = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 8 * 60)
        val c = base.copy(quietHours = q)
        assertEquals(Reason.QUIET_HOURS, decide("612345678", c, at(2026, 10, 14, 23)).reason)
        assertEquals(Reason.QUIET_HOURS, decide("612345678", c, at(2026, 10, 15, 7, 59)).reason)
        assertEquals(Reason.DEFAULT, decide("612345678", c, at(2026, 10, 15, 8)).reason)
        assertEquals(Reason.DEFAULT, decide("612345678", c, at(2026, 10, 14, 21, 59)).reason)
    }

    @Test fun quietHoursDayBelongsToStartDay() {
        // Solo viernes (5): el sábado a las 02:00 cuenta como franja del viernes.
        val q = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60, days = setOf(5))
        assertTrue(CallFilter.isInQuietHours(q, at(2026, 10, 17, 2), zone))  // sábado
        assertFalse(CallFilter.isInQuietHours(q, at(2026, 10, 18, 2), zone)) // domingo
        assertTrue(CallFilter.isInQuietHours(q, at(2026, 10, 16, 23, 30), zone)) // viernes
    }

    @Test fun quietHoursDoNotOverrideAllowlist() {
        val c = base.copy(
            quietHours = QuietHours(enabled = true, startMinute = 0, endMinute = 23 * 60 + 59),
            allowed = listOf(NumberEntry("+34612345678")),
        )
        assertEquals(CallAction.ALLOW, decide("612345678", c).action)
        assertEquals(CallAction.SILENCE, decide("612000000", c).action)
    }
}

class CommunityTest {

    @Test fun parsesAndDropsBadEntries() {
        val text = """
            {"version":3,"generatedAt":1,"minReports":3,"entries":[
              {"n":"+34612345678","r":5,"c":"energia"},
              {"n":"612345679","r":5,"c":"energia"},
              {"n":"+34612345670","r":1,"c":"robot"},
              {"n":"1004","r":9,"c":"otro"},
              {"n":"+34900111222","r":4,"c":"comercial","x":true}
            ]}
        """.trimIndent()
        val idx = CommunityIndex.parse(text)
        assertEquals(2, idx.size)
        assertEquals(SpamCategory.ENERGY, idx.lookup("+34612345678")?.category)
        assertNull(idx.lookup("+34612345670"))
        assertEquals(3L, idx.version)
    }

    @Test fun reportValidation() {
        val id = "abcdefghijklmnop1234"
        assertEquals("+34400123456", SpamReport.create("400 123 456", SpamCategory.SALES, id)?.number)
        assertNull(SpamReport.create("016", SpamCategory.SALES, id))
        assertNull(SpamReport.create("400123456", SpamCategory.SALES, "short"))
    }
}
