package com.cazadoxs.escanerlimpio

import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.classify.DocumentAnalyzer
import com.cazadoxs.escanerlimpio.classify.TextTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocumentAnalyzerTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun payslip() {
        val a = DocumentAnalyzer.analyze(
            "RECIBO INDIVIDUAL JUSTIFICATIVO DE SALARIOS Periodo de liquidación: marzo 2026 Total devengado 2.150,00 Total a deducir 410,20 LÍQUIDO A PERCIBIR 1.739,80",
            today,
        )
        assertEquals(Category.NOMINAS, a.category)
        assertEquals(LocalDate.of(2026, 3, 1), a.docDate)
        assertEquals(1739.80, a.amount!!, 0.001)
        assertEquals("Nómina · marzo 2026", a.title)
    }

    @Test fun utilityInvoice() {
        val a = DocumentAnalyzer.analyze(
            "Iberdrola Clientes Factura nº 123 Fecha de factura: 14/03/2026 Periodo de facturación CUPS ES001 Potencia contratada 4,6 kW Total factura 63,45 € IVA 21%",
            today,
        )
        assertEquals(Category.FACTURAS, a.category)
        assertEquals("Iberdrola", a.issuer)
        assertEquals(63.45, a.amount!!, 0.001)
        assertEquals("Factura Iberdrola · marzo 2026", a.title)
    }

    @Test fun receiptIsTicketNotInvoice() {
        val a = DocumentAnalyzer.analyze("MERCADONA Factura simplificada Gracias por su compra TOTAL COMPRA 23,10 Efectivo 25,00 Cambio 1,90 IVA incluido", today)
        assertEquals(Category.TICKETS, a.category)
    }

    @Test fun dniValidatedByLetter() {
        assertTrue(DocumentAnalyzer.containsValidDni("12345678Z"))
        assertFalse(DocumentAnalyzer.containsValidDni("12345678A"))
        val a = DocumentAnalyzer.analyze("DOCUMENTO NACIONAL DE IDENTIDAD ESPAÑA DNI 12345678Z Fecha de nacimiento 01 01 1990 Nacionalidad ESP", today)
        assertEquals(Category.IDENTIDAD, a.category)
        assertEquals("DNI", a.title)
        assertNull(a.docDate)
    }

    @Test fun nieValidated() {
        // X1234567 -> 01234567 % 23 = 14 -> 'L'
        assertTrue(DocumentAnalyzer.containsValidNie("X1234567L"))
        assertFalse(DocumentAnalyzer.containsValidNie("X1234567A"))
    }

    @Test fun unknownTextFallsBackToOthers() {
        assertEquals(Category.OTROS, DocumentAnalyzer.analyze("lista de la compra: pan, leche", today).category)
    }

    @Test fun futureAndAncientDatesIgnored() {
        assertNull(DocumentAnalyzer.findDate("fecha 01/01/2099", Category.FACTURAS, today))
        assertNull(DocumentAnalyzer.findDate("fecha 01/01/1950", Category.FACTURAS, today))
        assertEquals(LocalDate.of(2025, 12, 31), DocumentAnalyzer.findDate("emitida el 31 de diciembre de 2025", Category.FACTURAS, today))
    }

    @Test fun amountParsing() {
        assertEquals(1234.56, DocumentAnalyzer.parseAmount("1.234,56")!!, 0.001)
        assertEquals(12.5, DocumentAnalyzer.parseAmount("12.50")!!, 0.001)
    }

    @Test fun safeFileNames() {
        assertEquals("a b c", TextTools.safeFileName("a/b\\c"))
        assertEquals("Documento", TextTools.safeFileName("..."))
        assertEquals("passwd", TextTools.safeFileName("../../passwd"))
        assertEquals("factura iberdrola", TextTools.normalize("Factura  Iberdrola"))
        assertEquals("camion", TextTools.normalize("Camión"))
    }
}
