package com.cazadoxs.descifra.core

import com.cazadoxs.descifra.core.ai.AiMerge
import com.cazadoxs.descifra.core.model.AnalysisSource
import com.cazadoxs.descifra.core.model.DeadlineKind
import com.cazadoxs.descifra.core.model.ReferenceKind
import com.cazadoxs.descifra.core.model.Urgency
import com.cazadoxs.descifra.core.model.formatEuros
import com.cazadoxs.descifra.core.privacy.Redactor
import com.cazadoxs.descifra.core.rules.AmountExtractor
import com.cazadoxs.descifra.core.rules.BusinessDays
import com.cazadoxs.descifra.core.rules.DateExtractor
import com.cazadoxs.descifra.core.rules.DateRole
import com.cazadoxs.descifra.core.rules.LocalAnalyzer
import com.cazadoxs.descifra.core.rules.PeriodUnit
import com.cazadoxs.descifra.core.rules.ReferenceExtractor
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreTest {

    private val requerimiento = """
        AGENCIA TRIBUTARIA
        Delegación Especial de Valencia
        Fecha: 1 de octubre de 2026
        D. JUAN PÉREZ GARCÍA   NIF: 12345678Z
        C/ Mayor 12, 3º B, 46001 Valencia
        Nº de expediente: 202646123456X
        REQUERIMIENTO
        En relación con su declaración del Impuesto sobre la Renta de las Personas Físicas (IRPF),
        ejercicio 2025, se le requiere para que en el plazo de diez (10) días hábiles, contados a partir
        del día siguiente al de la notificación, aporte la siguiente documentación: facturas de las
        obras de mejora de la vivienda y justificante de pago.
        Código Seguro de Verificación: ABCD1234EFGH5678
    """.trimIndent()

    private val apremio = """
        Agencia Tributaria. PROVIDENCIA DE APREMIO
        Fecha de emisión: 05/10/2026
        Deuda: IRPF 2024. Importe principal: 1.234,56 €
        Recargo de apremio reducido (10%): 123,46 €
        Total a ingresar: 1.358,02 €
        Si no realiza el ingreso se procederá al embargo de sus bienes.
        Puede domiciliar el pago en la cuenta ES91 2100 0418 4502 0005 1332.
    """.trimIndent()

    private val multa = """
        DIRECCIÓN GENERAL DE TRÁFICO - Jefatura Provincial de Tráfico de Madrid
        Boletín de denuncia. Vehículo matrícula 1234 BCD.
        Infracción: exceso de velocidad. Multa: 300 euros.
        Si abona la multa antes del 30 de octubre de 2026, se beneficiará de una reducción del 50%, es decir, 150,00 €.
        Dispone de 20 días naturales para presentar alegaciones.
    """.trimIndent()

    @Test
    fun businessDaysSkipWeekendsAndHolidays() {
        // Fri 9 Oct 2026 + 2 business days: Mon 12 Oct is a holiday -> Tue 13, Wed 14.
        assertEquals(LocalDate.of(2026, 10, 14), BusinessDays.addBusinessDays(LocalDate.of(2026, 10, 9), 2))
        assertEquals(LocalDate.of(2026, 4, 5), BusinessDays.easterSunday(2026))
        assertFalse(BusinessDays.isBusinessDay(LocalDate.of(2026, 4, 3))) // Viernes Santo
    }

    @Test
    fun monthsAndLgtPeriods() {
        // 31 Jan + 1 month = 28 Feb 2026 (Saturday) -> Monday 2 March.
        assertEquals(LocalDate.of(2026, 3, 2), BusinessDays.addMonths(LocalDate.of(2026, 1, 31), 1))
        // Notified 10 Oct -> 20 Nov; notified 20 Oct -> 5 Dec is Saturday -> Mon 7 Dec (8 Dec holiday irrelevant).
        assertEquals(LocalDate.of(2026, 11, 20), BusinessDays.voluntaryPaymentDeadline(LocalDate.of(2026, 10, 10)))
        assertEquals(LocalDate.of(2026, 12, 7), BusinessDays.voluntaryPaymentDeadline(LocalDate.of(2026, 10, 20)))
        assertEquals(LocalDate.of(2026, 10, 20), BusinessDays.enforcementPaymentDeadline(LocalDate.of(2026, 10, 6)))
    }

    @Test
    fun extractsDatesWithRoles() {
        val dates = DateExtractor.dates(multa + "\nEn Madrid, a 2 de octubre de 2026", LocalDate.of(2026, 10, 10))
        val deadline = dates.first { it.role == DateRole.DEADLINE }
        assertEquals(LocalDate.of(2026, 10, 30), deadline.date)
        assertEquals(DeadlineKind.PAY, deadline.kind)
        assertTrue(dates.any { it.role == DateRole.ISSUE && it.date == LocalDate.of(2026, 10, 2) })
    }

    @Test
    fun extractsPeriods() {
        val p = DateExtractor.periods(requerimiento).single()
        assertEquals(10, p.amount)
        assertEquals(PeriodUnit.BUSINESS_DAYS, p.unit)
        assertEquals(DeadlineKind.PROVIDE_DOCS, p.kind)

        val natural = DateExtractor.periods(multa).single()
        assertEquals(PeriodUnit.CALENDAR_DAYS, natural.unit)
        assertEquals(DeadlineKind.APPEAL, natural.kind)
    }

    @Test
    fun amountsAndLabels() {
        val amounts = AmountExtractor.extract(apremio)
        assertEquals(listOf(123456L, 12346L, 135802L), amounts.map { it.cents })
        assertEquals("Total a pagar", amounts.last().label)
        assertEquals("Recargo", amounts[1].label)
        assertEquals("1.358,02 €", formatEuros(135802))
    }

    @Test
    fun referencesAreValidated() {
        val refs = ReferenceExtractor.extract(requerimiento + "\nNIF falso 12345678A")
        assertTrue(refs.any { it.kind == ReferenceKind.NIF && it.value == "12345678Z" })
        assertFalse(refs.any { it.value == "12345678A" })
        assertTrue(refs.any { it.kind == ReferenceKind.CSV && it.value == "ABCD1234EFGH5678" })
        assertTrue(refs.any { it.kind == ReferenceKind.EXPEDIENTE && it.value == "202646123456X" })
        assertTrue(ReferenceExtractor.validIban("ES9121000418450200051332"))
        assertTrue(ReferenceExtractor.extract(multa).any { it.kind == ReferenceKind.MATRICULA && it.value == "1234BCD" })
    }

    @Test
    fun analyzesRequerimiento() {
        val a = LocalAnalyzer.analyze(requerimiento, received = LocalDate.of(2026, 10, 9), today = LocalDate.of(2026, 10, 9))
        assertEquals("aeat", a.issuer?.id)
        assertEquals("Requerimiento", a.docType)
        val d = a.deadlines.single()
        assertEquals("2026-10-26", d.date) // 10 business days from Fri 9 Oct, skipping 12 Oct.
        assertTrue(d.estimated)
        assertEquals("2026-10-01", a.issueDate)
        assertEquals(Urgency.MEDIUM, a.urgency)
    }

    @Test
    fun analyzesApremioWithLegalDefault() {
        val a = LocalAnalyzer.analyze(apremio, received = LocalDate.of(2026, 10, 8), today = LocalDate.of(2026, 10, 8))
        assertEquals("Providencia de apremio", a.docType)
        val d = a.deadlines.first { it.kind == DeadlineKind.PAY }
        assertEquals("2026-10-20", d.date)
        assertNotNull(d.basis)
        assertTrue(a.references.any { it.kind == ReferenceKind.IBAN })
        assertTrue(a.summary.contains("1.358,02 €"))
    }

    @Test
    fun analyzesTrafficFine() {
        val a = LocalAnalyzer.analyze(multa, received = LocalDate.of(2026, 10, 12), today = LocalDate.of(2026, 10, 12))
        assertEquals("dgt", a.issuer?.id)
        assertEquals("Multa o sanción", a.docType)
        assertTrue(a.deadlines.any { it.date == "2026-10-30" && it.kind == DeadlineKind.PAY && !it.estimated })
        // 20 natural days from 12 Oct = 1 Nov (Sunday + holiday) -> Mon 2 Nov.
        assertTrue(a.deadlines.any { it.date == "2026-11-02" && it.kind == DeadlineKind.APPEAL })
    }

    @Test
    fun redactsAndRestores() {
        val r = Redactor().redact(requerimiento + "\nTeléfono 612 345 678, email juan@example.com")
        assertFalse(r.text.contains("12345678Z"), r.text)
        assertFalse(r.text.contains("612 345 678"), r.text)
        assertFalse(r.text.contains("juan@example.com"), r.text)
        assertFalse(r.text.contains("JUAN PÉREZ"), r.text)
        assertTrue(r.text.contains("[NIF_1]"))
        assertEquals("Tu NIF es 12345678Z", r.restore("Tu NIF es [NIF_1]"))
    }

    @Test
    fun aiDeadlinesAreVerifiedAgainstTheLetter() {
        val local = LocalAnalyzer.analyze(multa, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12))
        val raw = """
            ```json
            {"title":"Multa de tráfico","summary":"Te han multado por velocidad.","docType":"Multa",
             "whatTheyAsk":["Pagar o alegar"],"steps":["Paga con descuento"],
             "deadlines":[
               {"label":"Pagar con descuento","date":"2026-10-30","kind":"PAY","estimated":false,"quote":"antes del 30 de octubre de 2026"},
               {"label":"Inventada","date":"2026-12-24","kind":"OTHER","estimated":false,"quote":"plazo hasta nochebuena"}
             ],
             "amounts":[{"label":"Multa","euros":300,"quote":"Multa: 300 euros"},{"label":"Inventado","euros":999.99,"quote":""}],
             "warnings":[]}
            ```
        """.trimIndent()
        val merged = AiMerge.merge(raw, multa, local, AnalysisSource.CLOUD_AI, "test", LocalDate.of(2026, 10, 12))
        assertEquals("Multa de tráfico", merged.title)
        val invented = merged.deadlines.single { it.date == "2026-12-24" }
        assertFalse(invented.verified)
        assertTrue(merged.warnings.any { it.contains("no hemos encontrado") })
        assertFalse(merged.amounts.any { it.cents == 99999L })
        assertTrue(merged.amounts.any { it.cents == 30000L })
    }
}
