package com.cazadoxs.escanerlimpio.util

import com.cazadoxs.escanerlimpio.classify.DocumentAnalyzer
import com.cazadoxs.escanerlimpio.data.DocEntry
import java.util.Locale

object Format {
    private val es = Locale.forLanguageTag("es-ES")

    fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(es, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    fun money(value: Double): String = String.format(es, "%,.2f €", value)

    fun pages(n: Int) = if (n == 1) "1 página" else "$n páginas"

    fun docSubtitle(d: DocEntry): String = buildList {
        add(DocumentAnalyzer.fullDateLabel(d.effectiveDate))
        add(pages(d.pageCount))
        d.amount?.let { add(money(it)) }
    }.joinToString(" · ")
}
