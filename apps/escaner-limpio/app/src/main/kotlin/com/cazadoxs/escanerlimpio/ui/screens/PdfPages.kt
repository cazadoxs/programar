package com.cazadoxs.escanerlimpio.ui.screens

import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.cazadoxs.escanerlimpio.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Renders pages of one PDF on demand. PdfRenderer allows a single open page, hence the lock. */
class PageSource(file: File) {
    private val renderer: PdfRenderer? = runCatching { PdfTools.openRenderer(file) }.getOrNull()
    private val lock = Mutex()
    private val cache = LruCache<String, ImageBitmap>(8)
    private var closed = false

    val pageCount: Int = renderer?.pageCount ?: 0

    suspend fun render(index: Int, width: Int): ImageBitmap? {
        val key = "$index@$width"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                if (closed || renderer == null || index !in 0 until pageCount) return@withLock null
                runCatching { PdfTools.renderPage(renderer, index, width).asImageBitmap() }.getOrNull()?.also { cache.put(key, it) }
            }
        }
    }

    suspend fun close() {
        lock.withLock {
            closed = true
            runCatching { renderer?.close() }
        }
    }
}

@Composable
fun rememberPageSource(file: File, version: Any): PageSource {
    val source = remember(file, version) { PageSource(file) }
    DisposableEffect(source) {
        onDispose { kotlinx.coroutines.runBlocking { source.close() } }
    }
    return source
}
