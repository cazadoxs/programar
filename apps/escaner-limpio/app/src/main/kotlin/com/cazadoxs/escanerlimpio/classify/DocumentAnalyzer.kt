package com.cazadoxs.escanerlimpio.classify

import java.time.LocalDate

/** What the analyzer learned from a document's OCR text. */
data class Analysis(
    val category: Category,
    val title: String,
    val docDate: LocalDate?,
    val amount: Double?,
    val issuer: String?,
)

/**
 * Rule-based, fully offline classifier tuned for Spanish paperwork.
 * It picks a smart folder, a date, a total amount and the issuer, and
 * builds a readable title such as "Factura Iberdrola · marzo 2026".
 * Personal numbers (DNI, IBAN) are detected but never put in titles.
 */
object DocumentAnalyzer {

    private class Rule(val phrase: String, val weight: Double) {
        val regex = Regex("(?<![a-z0-9])" + Regex.escape(phrase) + "(?![a-z0-9])")
    }

    private fun rules(vararg pairs: Pair<String, Number>) = pairs.map { Rule(it.first, it.second.toDouble()) }

    private val categoryRules: Map<Category, List<Rule>> = mapOf(
        Category.IDENTIDAD to rules(
            "documento nacional de identidad" to 6, "dni" to 2, "pasaporte" to 4, "passport" to 3,
            "idesp" to 5, "permiso de residencia" to 5, "tarjeta de identidad de extranjero" to 6,
            "nacionalidad" to 1, "lugar de nacimiento" to 2, "fecha de nacimiento" to 1, "reino de espana" to 1,
        ),
        Category.NOMINAS to rules(
            "nomina" to 4, "recibo de salarios" to 6, "recibo individual justificativo" to 6,
            "salario base" to 4, "liquido a percibir" to 6, "liquido total a percibir" to 6,
            "total devengado" to 4, "total a deducir" to 4, "contingencias comunes" to 3,
            "base de cotizacion" to 2, "periodo de liquidacion" to 3, "devengos" to 3, "deducciones" to 1,
            "pagas extras" to 2, "grupo de cotizacion" to 3,
        ),
        Category.FACTURAS to rules(
            "factura" to 3, "numero de factura" to 3, "n de factura" to 3, "fecha de factura" to 3,
            "base imponible" to 3, "total factura" to 3, "importe total" to 1, "iva" to 1,
            "periodo de facturacion" to 4, "cups" to 3, "kwh" to 2, "suministro" to 2, "potencia contratada" to 3,
            "termino fijo" to 2, "cuota" to 1, "fecha de emision" to 1,
        ),
        Category.TICKETS to rules(
            "ticket" to 3, "factura simplificada" to 5, "gracias por su visita" to 4, "gracias por su compra" to 4,
            "efectivo" to 2, "cambio" to 1, "tarjeta" to 1, "total compra" to 3, "iva incluido" to 2,
            "uds" to 1, "unidades" to 1, "caja" to 1, "devoluciones" to 2, "articulos" to 1, "tpv" to 2,
        ),
        Category.HACIENDA to rules(
            "agencia tributaria" to 6, "aeat" to 4, "hacienda" to 3, "modelo 100" to 6, "declaracion de la renta" to 6,
            "impuesto sobre la renta" to 5, "certificado de retenciones" to 6, "modelo 190" to 4, "modelo 303" to 5,
            "modelo 130" to 5, "informe de vida laboral" to 6, "vida laboral" to 5, "tesoreria general de la seguridad social" to 5,
            "borrador" to 1, "rendimientos del trabajo" to 3, "irpf" to 1,
        ),
        Category.BANCO to rules(
            "extracto" to 3, "iban" to 2, "saldo" to 2, "transferencia" to 2, "movimientos" to 2,
            "prestamo" to 3, "hipoteca" to 3, "cuenta corriente" to 3, "recibo domiciliado" to 3,
            "adeudo" to 2, "swift" to 2, "bic" to 1, "entidad" to 1, "tae" to 2, "tin" to 2,
        ),
        Category.CONTRATOS to rules(
            "contrato" to 3, "arrendamiento" to 4, "arrendador" to 3, "arrendatario" to 3, "clausula" to 2,
            "clausulas" to 2, "reunidos" to 2, "exponen" to 3, "las partes" to 2, "en prueba de conformidad" to 5,
            "fianza" to 2, "estipulaciones" to 3, "firmado" to 1,
        ),
        Category.SEGUROS to rules(
            "poliza" to 4, "aseguradora" to 3, "tomador" to 3, "asegurado" to 3, "prima" to 1,
            "siniestro" to 3, "coberturas" to 3, "seguro" to 2, "franquicia" to 2,
        ),
        Category.SALUD to rules(
            "paciente" to 3, "receta" to 3, "diagnostico" to 3, "informe medico" to 6, "hospital" to 3,
            "centro de salud" to 4, "analitica" to 3, "tarjeta sanitaria" to 4, "medicamento" to 2,
            "posologia" to 4, "servicio de salud" to 3, "colegiado" to 2, "vacuna" to 3, "alergias" to 2,
            "urgencias" to 3, "tratamiento" to 2, "consulta" to 1,
        ),
        Category.VEHICULO to rules(
            "permiso de circulacion" to 6, "matricula" to 3, "itv" to 4, "inspeccion tecnica" to 4,
            "bastidor" to 4, "dgt" to 3, "ficha tecnica" to 4, "vehiculo" to 2, "boletin de denuncia" to 4,
            "multa" to 2, "permiso de conducir" to 5, "conductor" to 1,
        ),
        Category.ESTUDIOS to rules(
            "certificado academico" to 6, "expediente academico" to 6, "universidad" to 3, "calificacion" to 3,
            "calificaciones" to 3, "curso academico" to 4, "alumno" to 2, "alumna" to 2, "asignatura" to 3,
            "creditos" to 2, "matricula de honor" to 2, "titulo de" to 1, "boletin de notas" to 5,
        ),
    )

    /** Known issuers, matched as whole words on normalized text. Ambiguous everyday words are left out. */
    private val issuers: List<Pair<Regex, String>> = listOf(
        "iberdrola" to "Iberdrola", "endesa" to "Endesa", "naturgy" to "Naturgy", "repsol" to "Repsol",
        "totalenergies" to "TotalEnergies", "holaluz" to "Holaluz", "octopus energy" to "Octopus Energy",
        "movistar" to "Movistar", "telefonica" to "Telefónica", "vodafone" to "Vodafone", "orange" to "Orange",
        "yoigo" to "Yoigo", "masmovil" to "MásMóvil", "jazztel" to "Jazztel", "pepephone" to "Pepephone",
        "lowi" to "Lowi", "simyo" to "Simyo", "digi" to "Digi", "mercadona" to "Mercadona", "carrefour" to "Carrefour",
        "lidl" to "Lidl", "aldi" to "Aldi", "alcampo" to "Alcampo", "eroski" to "Eroski", "el corte ingles" to "El Corte Inglés",
        "ikea" to "IKEA", "leroy merlin" to "Leroy Merlin", "decathlon" to "Decathlon", "mediamarkt" to "MediaMarkt",
        "media markt" to "MediaMarkt", "amazon" to "Amazon", "zara" to "Zara", "primark" to "Primark",
        "canal de isabel ii" to "Canal de Isabel II", "aqualia" to "Aqualia", "aigues de barcelona" to "Aigües de Barcelona",
        "emasesa" to "Emasesa", "santander" to "Santander", "bbva" to "BBVA", "caixabank" to "CaixaBank",
        "sabadell" to "Sabadell", "bankinter" to "Bankinter", "unicaja" to "Unicaja", "abanca" to "Abanca",
        "kutxabank" to "Kutxabank", "ibercaja" to "Ibercaja", "openbank" to "Openbank", "revolut" to "Revolut",
        "mapfre" to "Mapfre", "mutua madrilena" to "Mutua Madrileña", "allianz" to "Allianz", "generali" to "Generali",
        "linea directa" to "Línea Directa", "sanitas" to "Sanitas", "adeslas" to "Adeslas", "asisa" to "Asisa",
        "dkv" to "DKV", "renfe" to "Renfe", "iryo" to "Iryo", "agencia tributaria" to "Agencia Tributaria",
        "seguridad social" to "Seguridad Social", "dgt" to "DGT",
    ).map { (k, v) -> Regex("(?<![a-z0-9])" + Regex.escape(k) + "(?![a-z0-9])") to v }

    private val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val monthAlternation = (months + "setiembre").joinToString("|")

    private val numericDate = Regex("(?<!\\d)(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4}|\\d{2})(?!\\d)")
    private val longDate = Regex("(?<!\\d)(\\d{1,2}) de ($monthAlternation)(?: de| del)? (\\d{4})(?!\\d)")
    private val monthYear = Regex("(?<![a-z])($monthAlternation)(?: de| del)? (\\d{4})(?!\\d)")
    private val totalAmount = Regex(
        "(?:total|importe|liquido a percibir|a pagar|a percibir)[^0-9\\n]{0,30}?(-?\\d{1,3}(?:[.\\s]\\d{3})*,\\d{2}|-?\\d+[.,]\\d{2})(?!\\d)",
    )
    private val netPay = Regex(
        "liquido (?:total )?a percibir[^0-9\\n]{0,30}?(-?\\d{1,3}(?:[.\\s]\\d{3})*,\\d{2}|-?\\d+[.,]\\d{2})(?!\\d)",
    )
    private val dniPattern = Regex("(?<![A-Z0-9])(\\d{8})[ -]?([A-Z])(?![A-Z0-9])")
    private val niePattern = Regex("(?<![A-Z0-9])([XYZ])[ -]?(\\d{7})[ -]?([A-Z])(?![A-Z0-9])")
    private const val DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"

    fun analyze(text: String, today: LocalDate = LocalDate.now()): Analysis {
        val norm = TextTools.normalize(text)
        val upper = text.uppercase()
        val hasDni = containsValidDni(upper)
        val hasNie = containsValidNie(upper)

        val scores = categoryRules.mapValues { (_, list) ->
            list.sumOf { rule -> if (rule.regex.containsMatchIn(norm)) rule.weight else 0.0 }
        }.toMutableMap()
        if (hasDni || hasNie) scores[Category.IDENTIDAD] = (scores[Category.IDENTIDAD] ?: 0.0) + 2.0
        // "factura simplificada" is a receipt, not a utility bill.
        if ("factura simplificada" in norm) scores[Category.FACTURAS] = (scores[Category.FACTURAS] ?: 0.0) - 3.0

        val best = scores.maxByOrNull { it.value }
        val category = if (best != null && best.value >= 3.0) best.key else Category.OTROS

        val date = findDate(norm, category, today)
        val amount = findAmount(norm, category)
        val issuer = findIssuer(norm)
        val title = buildTitle(category, norm, date, issuer, hasDni, hasNie)
        return Analysis(category, title, date, amount, issuer)
    }

    fun containsValidDni(upper: String): Boolean = dniPattern.findAll(upper).any { m ->
        val n = m.groupValues[1].toLong()
        DNI_LETTERS[(n % 23).toInt()].toString() == m.groupValues[2]
    }

    fun containsValidNie(upper: String): Boolean = niePattern.findAll(upper).any { m ->
        val prefix = "XYZ".indexOf(m.groupValues[1][0])
        val n = (prefix.toString() + m.groupValues[2]).toLong()
        DNI_LETTERS[(n % 23).toInt()].toString() == m.groupValues[3]
    }

    private fun monthIndex(name: String): Int =
        if (name == "setiembre") 9 else months.indexOf(name) + 1

    private fun plausible(d: LocalDate, today: LocalDate) =
        d.year >= 1990 && !d.isAfter(today.plusDays(60))

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()

    internal fun findDate(norm: String, category: Category, today: LocalDate): LocalDate? {
        if (category == Category.IDENTIDAD) return null
        // Payslips name a period ("marzo 2026"): prefer it over the signing date.
        if (category == Category.NOMINAS) {
            monthYear.findAll(norm).forEach { m ->
                safeDate(m.groupValues[2].toInt(), monthIndex(m.groupValues[1]), 1)
                    ?.takeIf { plausible(it, today) }?.let { return it }
            }
        }
        data class Hit(val pos: Int, val date: LocalDate)
        val hits = mutableListOf<Hit>()
        longDate.findAll(norm).forEach { m ->
            safeDate(m.groupValues[3].toInt(), monthIndex(m.groupValues[2]), m.groupValues[1].toInt())
                ?.let { hits += Hit(m.range.first, it) }
        }
        numericDate.findAll(norm).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            var year = m.groupValues[3].toInt()
            if (m.groupValues[3].length == 2) year += if (year <= 50) 2000 else 1900
            safeDate(year, month, day)?.let { hits += Hit(m.range.first, it) }
        }
        return hits.filter { plausible(it.date, today) }.minByOrNull { it.pos }?.date
            ?: monthYear.findAll(norm).firstNotNullOfOrNull { m ->
                safeDate(m.groupValues[2].toInt(), monthIndex(m.groupValues[1]), 1)?.takeIf { plausible(it, today) }
            }
    }

    internal fun parseAmount(raw: String): Double? {
        val s = raw.replace(" ", "")
        val normalized = when {
            s.contains(',') -> s.replace(".", "").replace(',', '.')
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    internal fun findAmount(norm: String, category: Category = Category.OTROS): Double? {
        // A payslip lists gross and net: the figure that matters is the net pay.
        if (category == Category.NOMINAS) {
            netPay.find(norm)?.let { m -> parseAmount(m.groupValues[1])?.takeIf { it > 0 }?.let { return it } }
        }
        return totalAmount.findAll(norm)
            .mapNotNull { parseAmount(it.groupValues[1]) }
            .filter { it > 0 && it < 1_000_000 }
            .maxOrNull()
    }

    internal fun findIssuer(norm: String): String? =
        issuers.mapNotNull { (regex, name) -> regex.find(norm)?.let { it.range.first to name } }
            .minByOrNull { it.first }?.second

    fun monthYearLabel(d: LocalDate) = "${months[d.monthValue - 1]} ${d.year}"
    fun fullDateLabel(d: LocalDate) = "${d.dayOfMonth} ${months[d.monthValue - 1].take(3)} ${d.year}"

    private fun buildTitle(
        category: Category,
        norm: String,
        date: LocalDate?,
        issuer: String?,
        hasDni: Boolean,
        hasNie: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        when (category) {
            Category.IDENTIDAD -> return when {
                "pasaporte" in norm || "passport" in norm -> "Pasaporte"
                hasNie || "extranjero" in norm || "permiso de residencia" in norm -> "NIE / TIE"
                hasDni || "dni" in norm -> "DNI"
                else -> category.singular
            }
            Category.NOMINAS -> {
                parts += "Nómina"
                if (issuer != null && issuer != "Seguridad Social") parts += issuer
            }
            Category.HACIENDA -> parts += when {
                "certificado de retenciones" in norm -> "Certificado de retenciones"
                "vida laboral" in norm -> "Vida laboral"
                "modelo 100" in norm || "declaracion de la renta" in norm -> "Declaración de la Renta"
                "modelo 303" in norm -> "Modelo 303 (IVA)"
                "modelo 130" in norm -> "Modelo 130"
                else -> "Hacienda"
            }
            Category.VEHICULO -> {
                parts += when {
                    "permiso de circulacion" in norm -> "Permiso de circulación"
                    "permiso de conducir" in norm -> "Permiso de conducir"
                    "itv" in norm || "inspeccion tecnica" in norm -> "ITV"
                    "multa" in norm || "denuncia" in norm -> "Multa"
                    else -> category.singular
                }
            }
            Category.CONTRATOS -> {
                parts += if ("arrendamiento" in norm || "alquiler" in norm) "Contrato de alquiler" else "Contrato"
                issuer?.let { parts += it }
            }
            else -> {
                parts += category.singular
                issuer?.let { parts += it }
            }
        }
        val head = parts.joinToString(" ")
        if (date == null) return head
        val dateLabel = when (category) {
            Category.TICKETS -> fullDateLabel(date)
            Category.HACIENDA, Category.VEHICULO, Category.SALUD, Category.CONTRATOS -> fullDateLabel(date)
            else -> monthYearLabel(date)
        }
        return "$head · $dateLabel"
    }
}
