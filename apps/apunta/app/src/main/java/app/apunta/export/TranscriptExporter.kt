package app.apunta.export

import app.apunta.util.TimeFormat

/** A transcript line independent of the database entity, so formatting is unit-testable. */
data class Line(val startMs: Long, val endMs: Long, val text: String)

enum class ExportFormat(val extension: String, val mime: String) {
    TXT("txt", "text/plain"),
    TXT_TIMESTAMPS("txt", "text/plain"),
    SRT("srt", "application/x-subrip"),
    VTT("vtt", "text/vtt"),
    MARKDOWN("md", "text/markdown"),
}

object TranscriptExporter {

    fun export(format: ExportFormat, title: String, lines: List<Line>, summary: String? = null): String =
        when (format) {
            ExportFormat.TXT -> plain(lines)
            ExportFormat.TXT_TIMESTAMPS -> timestamped(lines)
            ExportFormat.SRT -> srt(lines)
            ExportFormat.VTT -> vtt(lines)
            ExportFormat.MARKDOWN -> markdown(title, lines, summary)
        }

    /** Paragraphs: consecutive segments are joined, a long pause starts a new paragraph. */
    fun plain(lines: List<Line>, paragraphGapMs: Long = 2_000): String {
        val sb = StringBuilder()
        var prevEnd = -1L
        for (l in clean(lines)) {
            if (sb.isNotEmpty()) {
                sb.append(if (l.startMs - prevEnd >= paragraphGapMs) "\n\n" else " ")
            }
            sb.append(l.text)
            prevEnd = l.endMs
        }
        return sb.toString().trim() + "\n"
    }

    fun timestamped(lines: List<Line>): String =
        clean(lines).joinToString("\n") { "[${TimeFormat.clock(it.startMs)}] ${it.text}" } + "\n"

    fun srt(lines: List<Line>): String {
        val sb = StringBuilder()
        clean(lines).forEachIndexed { i, l ->
            sb.append(i + 1).append('\n')
            sb.append(TimeFormat.srt(l.startMs)).append(" --> ").append(TimeFormat.srt(fixEnd(l))).append('\n')
            sb.append(l.text).append("\n\n")
        }
        return sb.toString()
    }

    fun vtt(lines: List<Line>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        clean(lines).forEach { l ->
            sb.append(TimeFormat.vtt(l.startMs)).append(" --> ").append(TimeFormat.vtt(fixEnd(l))).append('\n')
            // "-->" inside a cue would end it early.
            sb.append(l.text.replace("-->", "->")).append("\n\n")
        }
        return sb.toString()
    }

    fun markdown(title: String, lines: List<Line>, summary: String?): String {
        val sb = StringBuilder()
        sb.append("# ").append(title.ifBlank { "Transcripción" }).append("\n\n")
        if (!summary.isNullOrBlank()) {
            sb.append("## Resumen\n\n").append(summary.trim()).append("\n\n")
        }
        sb.append("## Transcripción\n\n")
        clean(lines).forEach { l ->
            sb.append("**").append(TimeFormat.clock(l.startMs)).append("** ").append(l.text).append("\n\n")
        }
        return sb.toString()
    }

    /** Whisper segments start with a space and can be empty; subtitle players dislike both. */
    private fun clean(lines: List<Line>) = lines
        .map { it.copy(text = it.text.replace(Regex("\\s+"), " ").trim()) }
        .filter { it.text.isNotEmpty() }

    private fun fixEnd(l: Line) = if (l.endMs > l.startMs) l.endMs else l.startMs + 1_000

    /** A file name that is safe on every filesystem. */
    fun fileName(title: String, format: ExportFormat): String {
        val base = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "").trim().take(60).ifBlank { "apunta" }
        return "$base.${format.extension}"
    }
}
