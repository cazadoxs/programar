package com.cazadoxs.descifra.core.rules

import com.cazadoxs.descifra.core.model.DeadlineKind
import com.cazadoxs.descifra.core.model.Issuer
import com.cazadoxs.descifra.core.model.Urgency
import java.time.LocalDate

/** Who usually sends letters in Spain and how to recognise them. Patterns run on folded text. */
object Issuers {
    private data class Rule(val issuer: Issuer, val patterns: List<Regex>, val weight: Int = 1)

    private fun r(vararg p: String) = p.map { Regex(it) }

    private val rules = listOf(
        Rule(
            Issuer("aeat", "Agencia Estatal de Administración Tributaria", "Hacienda", "sede.agenciatributaria.gob.es", "91 554 87 70"),
            r("agencia (estatal de administracion )?tributaria", "\\baeat\\b", "hacienda publica", "agenciatributaria", "impuesto sobre la renta", "\\birpf\\b", "\\biva\\b.*(liquidacion|declaracion)"),
            2,
        ),
        Rule(
            Issuer("tgss", "Tesorería General de la Seguridad Social", "Seguridad Social", "sede.seg-social.gob.es"),
            r("seguridad social", "tesoreria general", "\\btgss\\b", "\\binss\\b", "seg-social", "instituto nacional de la seguridad social", "cotizacion"),
            2,
        ),
        Rule(
            Issuer("sepe", "Servicio Público de Empleo Estatal", "SEPE", "sede.sepe.gob.es"),
            r("\\bsepe\\b", "servicio publico de empleo", "prestacion por desempleo", "subsidio por desempleo"),
            2,
        ),
        Rule(
            Issuer("dgt", "Dirección General de Tráfico", "Tráfico", "sede.dgt.gob.es", "060"),
            r("direccion general de trafico", "\\bdgt\\b", "jefatura (provincial )?de trafico", "boletin de denuncia", "testra", "permiso de conducir"),
            2,
        ),
        Rule(
            Issuer("ayto", "Ayuntamiento", "Ayuntamiento"),
            r("ayuntamiento", "\\bajuntament\\b", "concello", "udala", "impuesto sobre bienes inmuebles", "\\bibi\\b", "plusvalia", "tasa de basuras", "ordenanza (municipal|fiscal)", "policia (local|municipal)"),
        ),
        Rule(
            Issuer("juzgado", "Juzgado o tribunal", "Juzgado"),
            r("juzgado", "tribunal", "procedimiento (monitorio|ordinario|verbal)", "letrado de la administracion de justicia", "diligencia de ordenacion", "decreto", "lexnet", "demanda"),
            2,
        ),
        Rule(
            Issuer("banco", "Entidad bancaria", "Banco"),
            r("\\bbanco\\b", "caixabank", "bbva", "santander", "sabadell", "bankinter", "unicaja", "ibercaja", "kutxabank", "abanca", "ing bank", "openbank", "entidad financiera", "prestamo", "tarjeta de credito", "descubierto", "cuenta corriente"),
        ),
        Rule(
            Issuer("casero", "Arrendador / casero", "Casero"),
            r("arrendador", "arrendatari", "contrato de arrendamiento", "\\balquiler\\b", "\\bfianza\\b", "renta mensual", "\\binquilin", "propietario de la vivienda", "\\bipc\\b"),
        ),
        Rule(
            Issuer("comunidad", "Comunidad de propietarios", "Comunidad"),
            r("comunidad de propietarios", "junta (general|ordinaria|extraordinaria)", "administrador de fincas", "derrama"),
        ),
        Rule(
            Issuer("suministros", "Compañía de suministros", "Suministros"),
            r("iberdrola", "endesa", "naturgy", "repsol", "totalenergies", "suministro (electrico|de gas|de agua)", "\\bcups\\b", "potencia contratada", "lectura del contador"),
        ),
        Rule(
            Issuer("autonomica", "Hacienda autonómica", "Hacienda autonómica"),
            r("agencia tributaria de (andalucia|catalunya|cataluna|madrid|galicia|canarias)", "atrian|\\batc\\b|\\batib\\b", "consejeria de hacienda", "impuesto sobre sucesiones", "transmisiones patrimoniales", "\\bitp\\b", "modelo 600", "modelo 650"),
            2,
        ),
        Rule(
            Issuer("seguro", "Aseguradora", "Seguro"),
            r("aseguradora", "poliza", "mapfre", "mutua", "\\baxa\\b", "allianz", "generali", "siniestro"),
        ),
    )

    fun detect(folded: String): Issuer? =
        rules.map { rule -> rule to rule.patterns.count { it.containsMatchIn(folded) } * rule.weight }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first?.issuer
}

/** A kind of letter, with a plain-language explanation and the legal default period if any. */
data class DocType(
    val id: String,
    val name: String,
    val explanation: String,
    val whatTheyAsk: String,
    val steps: List<String>,
    val baseUrgency: Urgency,
    val defaultKind: DeadlineKind,
    /** Legal default period when the letter does not spell one out. */
    val defaultDeadline: ((LocalDate) -> LocalDate)? = null,
    val defaultDeadlineBasis: String? = null,
    val patterns: List<Regex>,
)

object DocTypes {
    private fun r(vararg p: String) = p.map { Regex(it) }

    val all: List<DocType> = listOf(
        DocType(
            id = "apremio",
            name = "Providencia de apremio",
            explanation = "Es un aviso de cobro forzoso: no pagaste una deuda a tiempo y ahora se exige con un recargo. Si no pagas en el nuevo plazo, pueden embargar cuentas, nómina o devoluciones.",
            whatTheyAsk = "Pagar la deuda con el recargo indicado dentro del plazo.",
            steps = listOf(
                "Comprueba que la deuda es tuya y que no la habías pagado ya.",
                "Paga antes de la fecha límite para que el recargo no suba (del 10 % al 20 %).",
                "Si no puedes pagar de golpe, pide un aplazamiento o fraccionamiento en la sede electrónica antes de que acabe el plazo.",
                "Si crees que es un error, puedes presentar recurso de reposición en un mes.",
            ),
            baseUrgency = Urgency.CRITICAL,
            defaultKind = DeadlineKind.PAY,
            defaultDeadline = BusinessDays::enforcementPaymentDeadline,
            defaultDeadlineBasis = "Plazo de pago en apremio (art. 62.5 LGT), contado desde que recibiste la carta",
            patterns = r("providencia de apremio", "periodo ejecutivo", "recargo de apremio"),
        ),
        DocType(
            id = "embargo",
            name = "Diligencia de embargo",
            explanation = "Te comunican que ya han retenido dinero o bienes (cuenta, nómina, devolución) para cobrar una deuda.",
            whatTheyAsk = "Nada que firmar, pero conviene revisar la deuda y si el embargo respeta los límites legales.",
            steps = listOf(
                "Revisa qué deuda origina el embargo y su importe.",
                "Si el embargo afecta a tu nómina o pensión, comprueba que respeta el mínimo inembargable.",
                "Paga el resto de la deuda para levantar el embargo o recurre si hay un error.",
            ),
            baseUrgency = Urgency.HIGH,
            defaultKind = DeadlineKind.APPEAL,
            defaultDeadline = { BusinessDays.addMonths(it, 1) },
            defaultDeadlineBasis = "Plazo general de recurso de reposición: 1 mes",
            patterns = r("diligencia de embargo", "embargo de (cuentas|sueldo|salario|devolucion|creditos)", "\\bembarg"),
        ),
        DocType(
            id = "propuesta_liquidacion",
            name = "Propuesta de liquidación",
            explanation = "Hacienda ha revisado una declaración y cree que debes pagar más (o que te devuelve menos). Todavía no es definitivo: puedes dar tu versión.",
            whatTheyAsk = "Presentar alegaciones si no estás de acuerdo, o no hacer nada y esperar la liquidación definitiva.",
            steps = listOf(
                "Compara la propuesta con tu declaración y localiza la diferencia.",
                "Si no estás de acuerdo, presenta alegaciones con justificantes dentro del plazo.",
                "Si estás de acuerdo, puedes esperar la liquidación definitiva y pagar entonces.",
            ),
            baseUrgency = Urgency.HIGH,
            defaultKind = DeadlineKind.APPEAL,
            defaultDeadline = { BusinessDays.addBusinessDays(it, 10) },
            defaultDeadlineBasis = "Trámite de alegaciones habitual: 10 días hábiles",
            patterns = r("propuesta de liquidacion", "tramite de alegaciones", "puesta de manifiesto"),
        ),
        DocType(
            id = "requerimiento",
            name = "Requerimiento",
            explanation = "La administración te pide información o documentos. No suele ser una multa, pero si no respondes a tiempo puede acabar en sanción.",
            whatTheyAsk = "Aportar los documentos o la información que se detallan.",
            steps = listOf(
                "Haz una lista con cada documento que piden.",
                "Reúnelos y preséntalos por la sede electrónica o en registro antes del plazo.",
                "Guarda el justificante de presentación.",
            ),
            baseUrgency = Urgency.HIGH,
            defaultKind = DeadlineKind.PROVIDE_DOCS,
            defaultDeadline = { BusinessDays.addBusinessDays(it, 10) },
            defaultDeadlineBasis = "Plazo habitual de requerimientos: 10 días hábiles",
            patterns = r("requerimiento", "se le requiere", "le requerimos", "debera aportar", "aporte la siguiente documentacion"),
        ),
        DocType(
            id = "liquidacion",
            name = "Liquidación (deuda definitiva)",
            explanation = "Es la decisión final sobre lo que tienes que pagar. Ya no es una propuesta.",
            whatTheyAsk = "Pagar en periodo voluntario o recurrir si no estás de acuerdo.",
            steps = listOf(
                "Paga antes de que termine el periodo voluntario para evitar recargos.",
                "Puedes pedir aplazamiento o fraccionamiento dentro de ese periodo.",
                "Si no estás de acuerdo, recurso de reposición o reclamación económico-administrativa en 1 mes.",
            ),
            baseUrgency = Urgency.HIGH,
            defaultKind = DeadlineKind.PAY,
            defaultDeadline = BusinessDays::voluntaryPaymentDeadline,
            defaultDeadlineBasis = "Periodo voluntario de pago (art. 62.2 LGT), contado desde que recibiste la carta",
            patterns = r("liquidacion provisional", "acuerdo de liquidacion", "liquidacion definitiva", "carta de pago", "documento de ingreso", "modelo 002"),
        ),
        DocType(
            id = "sancion",
            name = "Multa o sanción",
            explanation = "Te comunican una infracción con una multa. Muchas permiten pagar con descuento si lo haces pronto, a cambio de no recurrir.",
            whatTheyAsk = "Pagar (con reducción si te interesa) o presentar alegaciones.",
            steps = listOf(
                "Comprueba los datos: fecha, lugar, matrícula o concepto.",
                "Si es correcta y quieres pagar, hazlo dentro del plazo de pronto pago para tener la reducción.",
                "Si no estás de acuerdo, presenta alegaciones en el plazo indicado.",
                "En multas de tráfico, si no conducías tú, identifica al conductor.",
            ),
            baseUrgency = Urgency.HIGH,
            defaultKind = DeadlineKind.PAY,
            defaultDeadline = { BusinessDays.addCalendarDays(it, 20) },
            defaultDeadlineBasis = "Pronto pago y alegaciones en multas de tráfico: 20 días naturales",
            patterns = r("denuncia", "infraccion", "sancion", "\\bmulta\\b", "pronto pago", "expediente sancionador", "boletin de denuncia"),
        ),
        DocType(
            id = "citacion",
            name = "Citación",
            explanation = "Te convocan a un lugar en una fecha y hora concretas.",
            whatTheyAsk = "Acudir el día indicado con la documentación que se pida.",
            steps = listOf(
                "Apunta la fecha, hora y dirección.",
                "Prepara el DNI y los documentos que se mencionen.",
                "Si no puedes ir, comunícalo antes y pide otra fecha.",
            ),
            baseUrgency = Urgency.HIGH,
            defaultKind = DeadlineKind.APPEAR,
            patterns = r("citacion", "se le cita", "debera comparecer", "comparecencia", "cita previa", "acudir el dia"),
        ),
        DocType(
            id = "judicial",
            name = "Notificación judicial",
            explanation = "Viene de un juzgado. Los plazos judiciales son cortos y no se suelen ampliar.",
            whatTheyAsk = "Leer qué se te pide exactamente (contestar, pagar, comparecer) y, casi siempre, consultar con un abogado.",
            steps = listOf(
                "Mira el tipo de procedimiento y el plazo.",
                "Consulta con un abogado o pide justicia gratuita si no puedes pagarlo.",
                "No dejes pasar el plazo: en un monitorio, no contestar en 20 días equivale a aceptar la deuda.",
            ),
            baseUrgency = Urgency.CRITICAL,
            defaultKind = DeadlineKind.RESPOND,
            patterns = r("procedimiento monitorio", "juicio verbal", "juicio ordinario", "emplazamiento", "requerimiento de pago judicial", "letrado de la administracion de justicia"),
        ),
        DocType(
            id = "reclamacion_deuda",
            name = "Reclamación de deuda",
            explanation = "Una empresa o banco te reclama un pago pendiente. No es una orden judicial, pero puede acabar en ficheros de morosos o en un juicio.",
            whatTheyAsk = "Pagar lo pendiente o explicar por qué no lo debes.",
            steps = listOf(
                "Comprueba que la deuda existe y el importe es correcto.",
                "Si es correcta, paga o negocia un calendario de pagos por escrito.",
                "Si no la reconoces, pide por escrito el detalle y el contrato que la origina.",
            ),
            baseUrgency = Urgency.MEDIUM,
            defaultKind = DeadlineKind.PAY,
            patterns = r("reclamacion de deuda", "importe pendiente", "recibo(s)? impagado", "regularizar (su|la) situacion", "fichero de morosos", "asnef", "deuda vencida"),
        ),
        DocType(
            id = "alquiler",
            name = "Comunicación del alquiler",
            explanation = "Tu casero te comunica algo sobre el contrato: subida de renta, renovación, fin de contrato o fianza.",
            whatTheyAsk = "Revisar si el cambio es conforme al contrato y a la ley de vivienda, y responder si hace falta.",
            steps = listOf(
                "Busca en tu contrato la cláusula de actualización de renta o duración.",
                "Comprueba que la subida no supera el índice legal aplicable.",
                "Responde por escrito (burofax o email con acuse) si no estás de acuerdo.",
            ),
            baseUrgency = Urgency.MEDIUM,
            defaultKind = DeadlineKind.RESPOND,
            patterns = r("actualizacion de (la )?renta", "subida (de la )?renta", "no renovacion", "fin del contrato", "preaviso", "devolucion de la fianza", "prorroga"),
        ),
        DocType(
            id = "resolucion",
            name = "Resolución",
            explanation = "Es la respuesta oficial a algo que pediste o a un procedimiento abierto (una ayuda, una prestación, un recurso).",
            whatTheyAsk = "Leer si te dan o te deniegan lo que pedías. Si no estás de acuerdo, puedes recurrir.",
            steps = listOf(
                "Mira si la resolución es favorable o desfavorable.",
                "Si es desfavorable, revisa el pie de recurso: dice qué recurso cabe y en qué plazo.",
            ),
            baseUrgency = Urgency.MEDIUM,
            defaultKind = DeadlineKind.APPEAL,
            defaultDeadline = { BusinessDays.addMonths(it, 1) },
            defaultDeadlineBasis = "Plazo general de recurso de alzada o reposición: 1 mes",
            patterns = r("\\bresolucion\\b", "\\bresuelve\\b", "se estima", "se desestima", "se concede", "se deniega"),
        ),
        DocType(
            id = "informativa",
            name = "Carta informativa",
            explanation = "Es una comunicación que solo informa. No parece pedirte que hagas nada.",
            whatTheyAsk = "Nada, salvo leerla y guardarla.",
            steps = listOf("Guárdala por si la necesitas más adelante."),
            baseUrgency = Urgency.LOW,
            defaultKind = DeadlineKind.INFO,
            patterns = r("le informamos", "le comunicamos", "a titulo informativo", "para su conocimiento", "datos fiscales", "certificado"),
        ),
    )

    fun detect(folded: String): DocType? =
        all.map { t -> t to t.patterns.count { it.containsMatchIn(folded) } }
            .filter { it.second > 0 }
            // Ties go to the earlier (more specific / more urgent) type in the list.
            .maxWithOrNull(compareBy<Pair<DocType, Int>> { it.second }.thenByDescending { all.indexOf(it.first) })
            ?.first
}
