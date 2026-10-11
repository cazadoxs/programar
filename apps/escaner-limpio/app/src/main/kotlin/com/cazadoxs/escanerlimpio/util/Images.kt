package com.cazadoxs.escanerlimpio.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

object Images {
    /** Decodes an image downsampled so its long side is at most [maxSide], honouring EXIF rotation. */
    fun decode(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Refuse absurd dimensions (decompression bombs).
        if (bounds.outWidth.toLong() * bounds.outHeight > 200_000_000L) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val longSide = max(decoded.width, decoded.height)
        val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
        if (rotation == 0 && scale == 1f) return decoded
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    fun writeThumb(bitmap: Bitmap, file: File, width: Int = 360) {
        val h = (bitmap.height * width.toFloat() / bitmap.width).toInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(bitmap, width, h, true)
        writeJpeg(thumb, file, 80)
        if (thumb !== bitmap) thumb.recycle()
    }
}
