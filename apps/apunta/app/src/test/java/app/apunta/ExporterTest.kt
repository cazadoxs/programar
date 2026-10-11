package app.apunta

import app.apunta.export.ExportFormat
import app.apunta.export.Line
import app.apunta.export.TranscriptExporter
import app.apunta.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExporterTest {
    private val lines = listOf(
        Line(0, 2_500, " Hola a todos."),
        Line(2_600, 5_000, "  Hoy vemos   el tema 3. "),
        Line(9_000, 9_000, "Después de una pausa."),
        Line(10_000, 11_000, "   "),
    )

    @Test fun srtNumbersCuesAndFixesEmptyDurations() {
        val srt = TranscriptExporter.srt(lines)
        assertTrue(srt.startsWith("1\n00:00:00,000 --> 00:00:02,500\nHola a todos.\n\n2\n"))
        assertTrue("zero-length cue gets 1 s", srt.contains("00:00:09,000 --> 00:00:10,000"))
        assertFalse("blank segments are dropped", srt.contains("\n4\n"))
    }

    @Test fun vttHasHeaderAndDotMillis() {
        val vtt = TranscriptExporter.vtt(listOf(Line(3_723_456, 3_725_000, "a --> b")))
        assertTrue(vtt.startsWith("WEBVTT\n\n01:02:03.456 --> 01:02:05.000\n"))
        assertFalse(vtt.substringAfter("\n\n").substringAfter('\n').contains("-->"))
    }

    @Test fun plainTextSplitsParagraphsOnLongPauses() {
        val t = TranscriptExporter.plain(lines)
        assertEquals("Hola a todos. Hoy vemos el tema 3.\n\nDespués de una pausa.\n", t)
    }

    @Test fun timestampedUsesClock() {
        assertEquals("[0:00] Hola a todos.\n[0:02] Hoy vemos el tema 3.\n[0:09] Después de una pausa.\n", TranscriptExporter.timestamped(lines))
    }

    @Test fun markdownIncludesSummary() {
        val md = TranscriptExporter.export(ExportFormat.MARKDOWN, "Clase", lines, "• Tema 3")
        assertTrue(md.startsWith("# Clase\n\n## Resumen\n\n• Tema 3\n\n## Transcripción"))
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("a b.srt", TranscriptExporter.fileName("a/b\\", ExportFormat.SRT).replace("ab", "a b"))
        assertEquals("apunta.txt", TranscriptExporter.fileName("///", ExportFormat.TXT))
        assertTrue(TranscriptExporter.fileName("../../etc/passwd", ExportFormat.TXT).none { it == '/' })
    }

    @Test fun timeFormats() {
        assertEquals("0:00", TimeFormat.clock(-5))
        assertEquals("1:02:03", TimeFormat.clock(3_723_000))
        assertEquals("2 h 5 min", TimeFormat.human(7_500_000))
        assertEquals("45 s", TimeFormat.human(45_000))
    }
}
