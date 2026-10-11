package io.github.cazadoxs.toma.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.Adherence
import io.github.cazadoxs.toma.core.DoseRecord
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.core.ExpectedDose
import io.github.cazadoxs.toma.core.Refill
import io.github.cazadoxs.toma.ui.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A printable summary for the doctor or pharmacist: what is taken, how often, and how it went. */
object PdfReport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val GREEN = 0xFF1F5E55.toInt()
    private const val INK = 0xFF1D2A27.toInt()
    private const val SOFT = 0xFF5C6A66.toInt()
    private const val LINE = 0xFFD9CFBF.toInt()
    private const val CORAL = 0xFFE36A4A.toInt()

    suspend fun create(context: Context, profileId: Long?, days: Int): File = withContext(Dispatchers.IO) {
        val repo = context.container.repository
        val zone = repo.zone
        val now = Instant.now()
        val fromDate = LocalDate.now().minusDays(days.toLong() - 1)
        val from = fromDate.atStartOfDay(zone).toInstant()
        val profiles = repo.profiles()
        val profileName = profileId?.let { id -> profiles.firstOrNull { it.id == id }?.name }
            ?: profiles.joinToString(", ") { it.name }
        val meds = repo.allMedications().filter { profileId == null || it.profileId == profileId }
        val medById = meds.associateBy { it.id }
        val records = repo.history(from, now.plusSeconds(1)).filter { it.medicationId in medById }
        val stats = repo.adherence(profileId, from, now, now)

        val doc = PdfDocument()
        val w = Writer(doc, context)
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        // Header band.
        w.canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 96f, Paint().apply { color = GREEN })
        w.canvas.drawText(context.getString(R.string.pdf_title), MARGIN, 46f, w.paint(22f, bold = true, color = 0xFFFFFFFF.toInt()))
        w.canvas.drawText(
            context.getString(R.string.pdf_subtitle, profileName, dateFmt.format(fromDate), dateFmt.format(LocalDate.now())),
            MARGIN, 72f, w.paint(11f, color = 0xFFDDEFEA.toInt()),
        )
        w.y = 124f

        w.heading(context.getString(R.string.pdf_section_meds))
        if (meds.isEmpty()) w.text(context.getString(R.string.pdf_none), 11f, color = SOFT)
        for (med in meds.sortedBy { it.name.lowercase() }) {
            w.ensure(54f)
            w.text(med.name + if (med.doseLabel.isNotBlank()) " · ${med.doseLabel}" else "", 12f, bold = true)
            val details = buildList {
                add(Formats.form(context, med.form))
                add(Formats.schedule(context, med.schedule))
                Formats.instruction(context, med.instruction)?.let(::add)
                med.stock?.let { stock ->
                    val left = Refill.daysLeft(stock, med.schedule)
                    add(
                        if (left != null) context.getString(R.string.pdf_stock_days, Formats.amount(stock), left)
                        else context.getString(R.string.pdf_stock, Formats.amount(stock)),
                    )
                }
                if (!med.active) add(context.getString(R.string.pdf_paused))
            }
            w.text(details.joinToString(" · "), 10f, color = SOFT)
            if (med.notes.isNotBlank()) w.text(med.notes, 10f, color = SOFT)
            w.y += 6f
        }

        w.heading(context.getString(R.string.pdf_section_adherence, days))
        val percent = stats.percent
        w.text(
            if (percent == null) context.getString(R.string.pdf_no_expected)
            else context.getString(R.string.pdf_adherence_line, percent, stats.taken, stats.expected, stats.skipped, stats.missed),
            12f,
        )
        for (med in meds.filter { it.active }) {
            val s = Adherence.stats(
                med.schedule.occurrences(maxOf(from, Instant.ofEpochMilli(med.createdAt)), now, zone)
                    .map { ExpectedDose(med.id, it.at) },
                records.filter { it.medicationId == med.id }
                    .map { DoseRecord(it.medicationId, Instant.ofEpochMilli(it.scheduledAt), it.status) },
                now,
            )
            s.percent?.let { w.text("• ${med.name}: $it %  (${s.taken}/${s.expected})", 10f, color = SOFT) }
        }

        w.heading(context.getString(R.string.pdf_section_log))
        if (records.isEmpty()) w.text(context.getString(R.string.pdf_none), 11f, color = SOFT)
        val cols = floatArrayOf(MARGIN, MARGIN + 110, MARGIN + 170, MARGIN + 400)
        for (r in records.sortedByDescending { it.scheduledAt }) {
            w.ensure(16f)
            val at = Instant.ofEpochMilli(r.scheduledAt).atZone(zone)
            val status = when (r.status) {
                DoseStatus.TAKEN -> context.getString(R.string.status_taken)
                DoseStatus.SKIPPED -> context.getString(R.string.status_skipped)
                DoseStatus.MISSED -> context.getString(R.string.status_missed)
            }
            val p = w.paint(9.5f)
            w.canvas.drawText(dateFmt.format(at), cols[0], w.y, p)
            w.canvas.drawText(timeFmt.format(at), cols[1], w.y, p)
            val name = TextUtils.ellipsize(medById[r.medicationId]?.name ?: "", p, 220f, TextUtils.TruncateAt.END).toString()
            w.canvas.drawText(name, cols[2], w.y, p)
            w.canvas.drawText(status, cols[3], w.y, w.paint(9.5f, bold = true, color = if (r.status == DoseStatus.TAKEN) GREEN else CORAL))
            w.y += 15f
        }

        w.finish()
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "toma-informe-${LocalDate.now()}.pdf")
        file.outputStream().use(doc::writeTo)
        doc.close()
        file
    }

    private class Writer(val doc: PdfDocument, private val ctx: Context) {
        private var pageNumber = 1
        private var page: PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        val canvas: Canvas get() = page.canvas
        var y = MARGIN + 10f

        fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_H - 70f) newPage()
        }

        private fun newPage() {
            footer()
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = MARGIN + 10f
        }

        fun heading(title: String) {
            ensure(40f)
            y += 10f
            canvas.drawText(title, MARGIN, y, paint(14f, bold = true, color = GREEN))
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = LINE; strokeWidth = 1f })
            y += 18f
        }

        /** Draws wrapped text and advances. */
        fun text(value: String, size: Float, bold: Boolean = false, color: Int = INK) {
            val p = paint(size, bold, color)
            val maxWidth = PAGE_W - 2 * MARGIN
            for (line in wrap(value, p, maxWidth)) {
                ensure(size + 6f)
                canvas.drawText(line, MARGIN, y, p)
                y += size + 5f
            }
        }

        private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
            val lines = ArrayList<String>()
            for (paragraph in text.split('\n')) {
                var current = ""
                for (word in paragraph.split(' ')) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (p.measureText(candidate) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotEmpty()) lines += current
                        current = word
                    }
                }
                lines += current
            }
            return lines
        }

        private fun footer() {
            val p = paint(8.5f, color = SOFT)
            canvas.drawText(ctx.getString(R.string.pdf_disclaimer), MARGIN, PAGE_H - 34f, p)
            canvas.drawText(ctx.getString(R.string.pdf_page, pageNumber), PAGE_W - MARGIN - 40f, PAGE_H - 20f, p)
        }

        fun finish() {
            footer()
            doc.finishPage(page)
        }
    }
}
