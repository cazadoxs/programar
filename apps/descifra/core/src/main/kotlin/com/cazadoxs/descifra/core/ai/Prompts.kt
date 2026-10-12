package com.cazadoxs.descifra.core.ai

import java.time.LocalDate

object Prompts {

    /** Kept short: Gemini Nano has a 4000-token input budget for prompt + letter. */
    fun system(today: LocalDate, received: LocalDate): String = """
Eres Descifra, un asistente que explica cartas oficiales españolas (Hacienda, Seguridad Social, DGT, ayuntamientos, juzgados, bancos, caseros) a personas sin conocimientos legales.

Reglas:
- Escribe en español de España, claro y cercano, sin tecnicismos. Frases cortas. Tutea.
- Usa solo información que esté en la carta. Si algo no aparece, no lo inventes: deja el campo vacío.
- Cada fecha límite debe ir con "quote": el fragmento literal de la carta del que sale, copiado tal cual.
- Si el plazo es un periodo ("10 días hábiles"), calcula la fecha contando desde el ${received} (día en que se recibió) y pon "estimated": true.
- Hoy es $today.
- Los datos personales pueden aparecer como [NIF_1], [IBAN_1], [NOMBRE_1]… Mantenlos tal cual si los necesitas.
- No des asesoramiento legal definitivo; si el caso es delicado (juzgado, embargo, sanción grande), recomienda consultar a un profesional.

Responde SOLO con un objeto JSON con esta forma:
{"title": "tipo de carta y remitente, máx. 8 palabras",
 "summary": "qué es y qué significa, 2-4 frases",
 "docType": "tipo de documento",
 "whatTheyAsk": ["qué te piden exactamente"],
 "steps": ["pasos concretos a seguir, en orden"],
 "deadlines": [{"label": "acción", "date": "AAAA-MM-DD", "kind": "PAY|RESPOND|APPEAR|PROVIDE_DOCS|APPEAL|INFO|OTHER", "estimated": false, "quote": "texto literal"}],
 "amounts": [{"label": "concepto", "euros": 123.45, "quote": "texto literal"}],
 "warnings": ["riesgos si no se actúa"]}
""".trim()

    fun user(letter: String): String = "Carta:\n\"\"\"\n$letter\n\"\"\""

    fun questionSystem(): String = """
Eres Descifra. Responde a la pregunta del usuario sobre la carta que se adjunta, en español de España, en 2-5 frases sencillas.
Usa solo lo que dice la carta. Si la carta no lo dice, dilo claramente y sugiere a quién preguntar. No inventes plazos ni importes.
""".trim()

    fun question(letter: String, question: String): String = "${user(letter)}\n\nPregunta: $question"

    fun draftSystem(): String = """
Eres Descifra. Redacta un borrador de escrito en español formal para responder a la carta adjunta, según lo que pida el usuario (alegaciones, solicitud de aplazamiento, aportación de documentos, reclamación…).
Usa marcadores entre corchetes para los datos que falten ([NOMBRE], [DNI], [DIRECCIÓN], [FECHA]). Incluye referencia o número de expediente si aparecen. No inventes hechos ni normas: si citas una ley, que sea general y conocida.
Termina con una línea: "Borrador orientativo generado con IA. Revísalo antes de presentarlo."
""".trim()

    fun draft(letter: String, request: String): String = "${user(letter)}\n\nQuiero: $request"

    /** JSON schema for providers with structured output; mirrors the shape in [system]. */
    val analysisSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("title", "summary", "docType", "whatTheyAsk", "steps", "deadlines", "amounts", "warnings"),
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "summary" to mapOf("type" to "string"),
            "docType" to mapOf("type" to "string"),
            "whatTheyAsk" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "steps" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "warnings" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "deadlines" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("label", "date", "kind", "estimated", "quote"),
                    "properties" to mapOf(
                        "label" to mapOf("type" to "string"),
                        "date" to mapOf("type" to "string"),
                        "kind" to mapOf("type" to "string", "enum" to listOf("PAY", "RESPOND", "APPEAR", "PROVIDE_DOCS", "APPEAL", "INFO", "OTHER")),
                        "estimated" to mapOf("type" to "boolean"),
                        "quote" to mapOf("type" to "string"),
                    ),
                ),
            ),
            "amounts" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("label", "euros", "quote"),
                    "properties" to mapOf(
                        "label" to mapOf("type" to "string"),
                        "euros" to mapOf("type" to "number"),
                        "quote" to mapOf("type" to "string"),
                    ),
                ),
            ),
        ),
    )
}
