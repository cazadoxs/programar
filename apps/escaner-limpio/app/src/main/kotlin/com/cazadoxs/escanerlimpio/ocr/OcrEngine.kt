package com.cazadoxs.escanerlimpio.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** OCR result for one page, in the pixel coordinates of the bitmap it ran on. */
data class OcrPage(val text: String, val words: List<OcrWord>, val width: Int, val height: Int)

/**
 * On-device text recognition with the bundled Latin model (Spanish accents
 * and ñ included). The model ships inside the APK: no download, no network.
 */
class OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): OcrPage {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val words = result.textBlocks.asSequence()
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { e ->
                val box = e.boundingBox ?: return@mapNotNull null
                if (e.text.isBlank() || box.width() <= 0 || box.height() <= 0) return@mapNotNull null
                OcrWord(e.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            }
            .toList()
        return OcrPage(result.text, words, bitmap.width, bitmap.height)
    }
}
