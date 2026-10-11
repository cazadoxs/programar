package com.cazadoxs.escanerlimpio.pdf

import com.cazadoxs.escanerlimpio.ocr.OcrPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/** One scanned page: a JPEG on disk plus the OCR result for that image. */
class PageImage(val jpeg: File, val width: Int, val height: Int, val ocr: OcrPage?)

/**
 * Builds "sandwich" PDFs: the scanned image with an invisible, selectable
 * and searchable text layer exactly on top of each recognised word.
 */
object PdfWriter {
    private const val SHORT_SIDE_PT = 595.276f // A4 width in points

    fun writeScan(pages: List<PageImage>, out: File) {
        PDDocument().use { doc ->
            val font = PDType1Font.HELVETICA
            val encoder = WinAnsiFilter(font)
            for (p in pages) {
                val (w, h) = if (p.width <= p.height) {
                    SHORT_SIDE_PT to SHORT_SIDE_PT * p.height / p.width
                } else {
                    SHORT_SIDE_PT * p.width / p.height to SHORT_SIDE_PT
                }
                val page = PDPage(PDRectangle(w, h))
                doc.addPage(page)
                val image = p.jpeg.inputStream().use { JPEGFactory.createFromStream(doc, it) }
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(image, 0f, 0f, w, h)
                    p.ocr?.let { addTextLayer(cs, font, encoder, it, w / p.width, 0f, h) }
                }
            }
            doc.save(out)
        }
    }

    /**
     * Writes invisible text for [ocr]. Pixel coordinates are scaled by [scale]
     * and placed relative to a page whose left edge is [originX] and top edge [topY].
     */
    fun addTextLayer(
        cs: PDPageContentStream,
        font: PDFont,
        encoder: WinAnsiFilter,
        ocr: OcrPage,
        scale: Float,
        originX: Float,
        topY: Float,
    ) {
        if (ocr.words.isEmpty()) return
        cs.beginText()
        cs.setRenderingMode(RenderingMode.NEITHER)
        for (word in ocr.words) {
            val text = encoder.filter(word.text)
            if (text.isBlank()) continue
            val boxH = (word.bottom - word.top) * scale
            val boxW = (word.right - word.left) * scale
            val fontSize = (boxH * 0.9f).coerceIn(1f, 400f)
            val natural = runCatching { font.getStringWidth(text) / 1000f * fontSize }.getOrDefault(0f)
            if (natural <= 0f || boxW <= 0f) continue
            cs.setFont(font, fontSize)
            cs.setHorizontalScaling((100f * boxW / natural).coerceIn(10f, 1000f))
            val x = originX + word.left * scale
            val y = topY - word.bottom * scale + boxH * 0.18f
            cs.setTextMatrix(Matrix.getTranslateInstance(x, y))
            cs.showText(text)
        }
        cs.endText()
    }
}

/** Keeps only characters the standard Helvetica (WinAnsi) encoding can write. */
class WinAnsiFilter(private val font: PDFont) {
    private val cache = HashMap<Char, Boolean>()

    fun filter(text: String): String = buildString {
        for (c in text) {
            val ok = cache.getOrPut(c) { runCatching { font.encode(c.toString()); true }.getOrDefault(false) }
            if (ok) append(c)
        }
    }
}
