package com.cazadoxs.escaneaycome.core

/** RFC 4180 CSV with protection against spreadsheet formula injection. */
object Csv {
    fun row(cells: List<String>): String = cells.joinToString(",") { escape(it) }

    fun escape(value: String): String {
        // A leading = + - @ tab or CR makes Excel/Sheets evaluate the cell as a formula.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun number(v: Double): String = String.format(java.util.Locale.ROOT, "%.1f", v)
}
