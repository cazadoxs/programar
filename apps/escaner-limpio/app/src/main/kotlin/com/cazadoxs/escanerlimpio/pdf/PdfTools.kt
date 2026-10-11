package com.cazadoxs.escanerlimpio.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Page-level PDF operations. All work happens on local files. */
object PdfTools {
    const val MAX_TEXT_CHARS = 40_000

    fun pageCount(file: File): Int = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }

    fun extractText(file: File): String = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
        PDFTextStripper().getText(doc).take(MAX_TEXT_CHARS)
    }

    fun merge(sources: List<File>, out: File) {
        val merger = PDFMergerUtility()
        sources.forEach { merger.addSource(it) }
        merger.destinationFileName = out.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    }

    /**
     * Writes a new PDF with the given pages of [src], in that order, each with an
     * extra clockwise rotation from [rotations] (degrees, multiples of 90).
     */
    fun rebuild(src: File, order: List<Int>, rotations: Map<Int, Int>, out: File) {
        require(order.isNotEmpty()) { "No pages" }
        PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            PDDocument().use { target ->
                for (index in order) {
                    require(index in 0 until source.numberOfPages) { "Page out of range" }
                    val imported = target.importPage(source.getPage(index))
                    val extra = rotations[index] ?: 0
                    if (extra != 0) imported.rotation = ((imported.rotation + extra) % 360 + 360) % 360
                }
                target.save(out)
            }
        }
    }

    /** Renders a page to a white-backed bitmap [targetWidth] pixels wide. */
    fun renderPage(renderer: PdfRenderer, index: Int, targetWidth: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    fun openRenderer(file: File): PdfRenderer =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
}
