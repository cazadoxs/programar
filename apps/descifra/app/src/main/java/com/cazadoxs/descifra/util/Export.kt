package com.cazadoxs.descifra.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.cazadoxs.descifra.core.rules.LocalAnalyzer
import com.cazadoxs.descifra.data.Letter
import java.io.File
import java.time.LocalDate

object Export {

    fun summaryText(letter: Letter): String = buildString {
        val a = letter.analysis
        appendLine(a.title)
        appendLine()
        appendLine(a.summary)
        if (a.whatTheyAsk.isNotEmpty()) {
            appendLine()
            appendLine("Qué te piden:")
            a.whatTheyAsk.forEach { appendLine("• $it") }
        }
        if (a.deadlines.isNotEmpty()) {
            appendLine()
            appendLine("Plazos:")
            a.deadlines.forEach {
                appendLine("• ${it.label}: ${LocalAnalyzer.formatLong(LocalDate.parse(it.date))}" + if (it.estimated) " (estimado)" else "")
            }
        }
        if (a.amounts.isNotEmpty()) {
            appendLine()
            appendLine("Importes:")
            a.amounts.forEach { appendLine("• ${it.label}: ${it.formatted}") }
        }
        if (a.steps.isNotEmpty()) {
            appendLine()
            appendLine("Pasos:")
            a.steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        }
        appendLine()
        appendLine("Resumen automático de Descifra. Compruébalo con la carta original; no es asesoramiento legal.")
    }.trim()

    /** A4 PDF with the explanation and, on following pages, the original text. */
    fun pdf(context: Context, letter: Letter): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "descifra-${letter.id.take(8)}.pdf")
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 48
        val width = pageW - margin * 2

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = 0xFF1B2A41.toInt() }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; color = 0xFF222222.toInt() }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF666666.toInt() }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin.toFloat()
        }
        fun write(text: String, paint: TextPaint, gap: Float = 8f) {
            var rest = text
            while (rest.isNotEmpty()) {
                val layout = StaticLayout.Builder.obtain(rest, 0, rest.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
                val room = pageH - margin - y
                if (page == null || room < paint.textSize * 3) { newPage(); continue }
                var lines = 0
                while (lines < layout.lineCount && layout.getLineBottom(lines) <= room) lines++
                if (lines == 0) { newPage(); continue }
                val end = layout.getLineEnd(lines - 1)
                val chunk = StaticLayout.Builder.obtain(rest, 0, end, paint, width).setLineSpacing(2f, 1f).build()
                val canvas = page!!.canvas
                canvas.save(); canvas.translate(margin.toFloat(), y); chunk.draw(canvas); canvas.restore()
                y += chunk.height + gap
                rest = rest.substring(end).trimStart('\n')
                if (rest.isNotEmpty()) newPage()
            }
        }

        newPage()
        write(letter.analysis.title, title, 12f)
        write(summaryText(letter).substringAfter("\n\n"), body, 14f)
        write("— Carta original (texto leído) —", small, 6f)
        newPage()
        write(letter.text, body)
        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
