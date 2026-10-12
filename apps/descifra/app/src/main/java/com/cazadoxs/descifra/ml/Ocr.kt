package com.cazadoxs.descifra.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device OCR (ML Kit bundled Latin model: works offline, nothing is uploaded). */
object Ocr {
    suspend fun read(context: Context, uris: List<Uri>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return uris.mapIndexed { _, uri ->
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image).await().text
            }.joinToString("\n\n").trim()
        } finally {
            recognizer.close()
        }
    }
}

/** Renders PDF pages (max 6) to bitmaps so they can be read by the same OCR. */
object PdfPages {
    suspend fun read(context: Context, uri: Uri): String {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                val out = StringBuilder()
                for (i in 0 until minOf(renderer.pageCount, 6)) {
                    renderer.openPage(i).use { page ->
                        val scale = 2f
                        val bmp = android.graphics.Bitmap.createBitmap(
                            (page.width * scale).toInt(), (page.height * scale).toInt(), android.graphics.Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.append(recognizer.process(InputImage.fromBitmap(bmp, 0)).await().text).append("\n\n")
                        bmp.recycle()
                    }
                }
                return out.toString().trim()
            }
        } finally {
            recognizer.close()
            runCatching { pfd.close() }
        }
    }
}
