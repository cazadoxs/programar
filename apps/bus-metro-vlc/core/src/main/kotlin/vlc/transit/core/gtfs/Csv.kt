package vlc.transit.core.gtfs

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Streaming RFC-4180 reader for GTFS text files. Handles a UTF-8 BOM, quoted
 * fields with embedded commas/quotes/newlines, CRLF, and the right-padding
 * some producers (Renfe) put on every field.
 */
class CsvReader(input: InputStream) : AutoCloseable {
    private val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16)
    private val sb = StringBuilder()
    private val fields = ArrayList<String>(16)
    val header: List<String>
    private val index: Map<String, Int>

    init {
        val h = nextRecord() ?: emptyList()
        header = h.toList().mapIndexed { i, s -> if (i == 0) s.removePrefix("﻿").trim() else s.trim() }
        index = header.withIndex().associate { it.value to it.index }
    }

    fun col(name: String): Int = index[name] ?: -1

    /** Calls [block] for each data row. The list is reused between calls; copy what you keep. */
    inline fun forEachRow(block: (Row) -> Unit) {
        val row = Row()
        while (true) {
            val r = nextRecord() ?: break
            if (r.size == 1 && r[0].isEmpty()) continue
            row.values = r
            block(row)
        }
    }

    fun nextRecord(): List<String>? {
        fields.clear()
        sb.setLength(0)
        var c = reader.read()
        if (c == -1) return null
        var inQuotes = false
        while (true) {
            if (c == -1) { fields += sb.toString(); return fields }
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    reader.mark(1)
                    val n = reader.read()
                    if (n == '"'.code) sb.append('"') else { inQuotes = false; if (n != -1) reader.reset() }
                } else sb.append(ch)
            } else when (ch) {
                '"' -> inQuotes = true
                ',' -> { fields += sb.toString(); sb.setLength(0) }
                '\r' -> {}
                '\n' -> { fields += sb.toString(); return fields }
                else -> sb.append(ch)
            }
            c = reader.read()
        }
    }

    override fun close() = reader.close()

    class Row {
        var values: List<String> = emptyList()
        operator fun get(i: Int): String = if (i in values.indices) values[i].trim() else ""
    }
}

/** "25:10:00" -> 90600. Returns -1 when empty or malformed. */
fun parseGtfsTime(s: String): Int {
    val t = s.trim()
    if (t.isEmpty()) return -1
    val a = t.indexOf(':')
    val b = t.indexOf(':', a + 1)
    if (a <= 0 || b <= a) return -1
    return try {
        val h = t.substring(0, a).toInt()
        val m = t.substring(a + 1, b).toInt()
        val sec = t.substring(b + 1).toInt()
        if (h < 0 || h > 47 || m !in 0..59 || sec !in 0..59) -1 else h * 3600 + m * 60 + sec
    } catch (_: NumberFormatException) { -1 }
}

/** "#E30613" / "e30613" / "" -> 0xFFE30613 or null. */
fun parseColor(s: String): Int? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}
