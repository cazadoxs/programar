package com.cazadoxs.escanerlimpio.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.classify.DocumentAnalyzer
import com.cazadoxs.escanerlimpio.classify.TextTools
import com.cazadoxs.escanerlimpio.ocr.OcrEngine
import com.cazadoxs.escanerlimpio.ocr.OcrPage
import com.cazadoxs.escanerlimpio.pdf.PageImage
import com.cazadoxs.escanerlimpio.pdf.PdfTools
import com.cazadoxs.escanerlimpio.pdf.PdfWriter
import com.cazadoxs.escanerlimpio.pdf.WinAnsiFilter
import com.cazadoxs.escanerlimpio.util.Images
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface Work {
    data object Idle : Work
    data class Running(val label: String, val done: Int, val total: Int) : Work
    data class Finished(val docId: String, val message: String) : Work
    data class Failed(val message: String) : Work
}

class UserFacingException(message: String) : Exception(message)

/**
 * Turns scans and imported files into library documents: OCR, searchable PDF,
 * smart folder, automatic name and thumbnail. Long jobs run in the app scope
 * so they survive screen changes, one at a time.
 */
class DocumentService(
    private val context: Context,
    private val store: DocumentStore,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val ocr = OcrEngine()
    private val jobLock = Mutex()
    private val _work = MutableStateFlow<Work>(Work.Idle)
    val work: StateFlow<Work> = _work.asStateFlow()

    private val workDir get() = File(context.cacheDir, "trabajo").apply { mkdirs() }

    fun dismissWork() {
        if (_work.value !is Work.Running) _work.value = Work.Idle
    }

    private fun launchJob(block: suspend () -> Unit) {
        scope.launch {
            jobLock.withLock {
                try {
                    block()
                } catch (e: UserFacingException) {
                    _work.value = Work.Failed(e.message ?: "No se pudo completar")
                } catch (e: OutOfMemoryError) {
                    _work.value = Work.Failed("El documento es demasiado grande para este móvil. Prueba con calidad Compacta.")
                } catch (e: Exception) {
                    Log.e(TAG, "Job failed", e)
                    _work.value = Work.Failed("Algo ha fallado al procesar el documento. Inténtalo de nuevo.")
                } finally {
                    workDir.listFiles()?.forEach { it.deleteRecursively() }
                }
            }
        }
    }

    /** Scanned or picked images → one searchable PDF. */
    fun importImages(uris: List<Uri>, folderId: String? = null) {
        if (uris.isEmpty()) return
        launchJob {
            val pages = uris.take(MAX_PAGES)
            val quality = settings.state.value.quality
            val id = store.newId()
            val images = mutableListOf<PageImage>()
            val text = StringBuilder()
            withContext(Dispatchers.Default) {
                pages.forEachIndexed { i, uri ->
                    _work.value = Work.Running("Leyendo página ${i + 1} de ${pages.size}", i, pages.size)
                    val bitmap = Images.decode(context.contentResolver, uri, quality.maxSide)
                        ?: throw UserFacingException("No se pudo abrir una de las imágenes.")
                    try {
                        val page: OcrPage? = runCatching { ocr.recognize(bitmap) }
                            .onFailure { Log.w(TAG, "OCR failed on page $i", it) }
                            .getOrNull()
                        val jpeg = File(workDir, "p$i.jpg")
                        Images.writeJpeg(bitmap, jpeg, quality.jpegQuality)
                        if (i == 0) Images.writeThumb(bitmap, store.thumbFile(id))
                        images += PageImage(jpeg, bitmap.width, bitmap.height, page)
                        page?.text?.let { text.appendLine(it) }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            _work.value = Work.Running("Creando PDF con texto", pages.size, pages.size)
            val out = store.pdfFile(id)
            withContext(Dispatchers.IO) { PdfWriter.writeScan(images, out) }
            saveEntry(id, text.toString(), images.size, out, folderId)
        }
    }

    /** An existing PDF from the phone. Adds an OCR layer to pages that have no text. */
    fun importPdf(uri: Uri, folderId: String? = null) {
        launchJob {
            _work.value = Work.Running("Copiando PDF", 0, 1)
            val id = store.newId()
            val tmp = File(workDir, "entrada.pdf")
            withContext(Dispatchers.IO) { copyLimited(uri, tmp, MAX_IMPORT_BYTES) }
            val out = store.pdfFile(id)
            val text = withContext(Dispatchers.Default) { addOcrLayer(tmp, out) }
            withContext(Dispatchers.IO) {
                PdfTools.openRenderer(out).use { r ->
                    val bmp = PdfTools.renderPage(r, 0, 360)
                    Images.writeJpeg(bmp, store.thumbFile(id), 80)
                    bmp.recycle()
                }
            }
            saveEntry(id, text, PdfTools.pageCount(out), out, folderId)
        }
    }

    private suspend fun addOcrLayer(src: File, out: File): String {
        val doc = try {
            PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly())
        } catch (e: InvalidPasswordException) {
            throw UserFacingException("Ese PDF está protegido con contraseña.")
        } catch (e: IOException) {
            throw UserFacingException("Ese archivo no es un PDF válido.")
        }
        doc.use {
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            val pageCount = doc.numberOfPages
            if (pageCount == 0) throw UserFacingException("El PDF no tiene páginas.")
            val existing = runCatching { com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc) }.getOrDefault("")
            if (existing.trim().length >= 40 * pageCount) {
                doc.save(out)
                return existing
            }
            val text = StringBuilder()
            val font = PDType1Font.HELVETICA
            val filter = WinAnsiFilter(font)
            PdfTools.openRenderer(src).use { renderer ->
                val limit = minOf(pageCount, renderer.pageCount, MAX_PAGES)
                for (i in 0 until limit) {
                    _work.value = Work.Running("Leyendo página ${i + 1} de $limit", i, limit)
                    val page = doc.getPage(i)
                    val bmp = PdfTools.renderPage(renderer, i, 2000)
                    try {
                        val result = ocr.recognize(bmp)
                        text.appendLine(result.text)
                        // Only unrotated pages get a positioned text layer; rotated ones stay searchable in the app.
                        if (page.rotation == 0 && result.words.isNotEmpty()) {
                            val box = page.cropBox
                            val scale = box.width / bmp.width
                            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                PdfWriter.addTextLayer(cs, font, filter, result, scale, box.lowerLeftX, box.upperRightY)
                            }
                        }
                    } finally {
                        bmp.recycle()
                    }
                }
            }
            doc.save(out)
            return existing + "\n" + text
        }
    }

    private suspend fun saveEntry(id: String, text: String, pages: Int, file: File, folderId: String?) {
        val analysis = DocumentAnalyzer.analyze(text)
        val title = if (settings.state.value.autoName) analysis.title else "Escaneo " + DocumentAnalyzer.fullDateLabel(LocalDate.now())
        val entry = DocEntry(
            id = id,
            title = uniqueTitle(title),
            category = analysis.category,
            folderId = folderId,
            createdAt = System.currentTimeMillis(),
            docDateEpochDay = analysis.docDate?.toEpochDay(),
            pageCount = pages,
            sizeBytes = file.length(),
            text = text.take(PdfTools.MAX_TEXT_CHARS),
            amount = analysis.amount,
            issuer = analysis.issuer,
        )
        store.add(entry)
        _work.value = Work.Finished(id, "Guardado en ${analysis.category.label}")
    }

    private fun uniqueTitle(title: String): String {
        val taken = store.library.value.docs.map { it.title }.toSet()
        if (title !in taken) return title
        var n = 2
        while ("$title ($n)" in taken) n++
        return "$title ($n)"
    }

    private fun copyLimited(uri: Uri, dest: File, limit: Long) {
        val input = context.contentResolver.openInputStream(uri) ?: throw UserFacingException("No se pudo abrir el archivo.")
        input.use { ins ->
            dest.outputStream().use { outs ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limit) throw UserFacingException("El archivo supera el máximo de 150 MB.")
                    outs.write(buf, 0, n)
                }
            }
        }
    }

    // ---- Pro tools -------------------------------------------------------

    fun merge(ids: List<String>, title: String) {
        if (ids.size < 2) return
        launchJob {
            _work.value = Work.Running("Uniendo ${ids.size} documentos", 0, 1)
            val docs = ids.mapNotNull(store::doc)
            val id = store.newId()
            val out = store.pdfFile(id)
            withContext(Dispatchers.IO) {
                PdfTools.merge(docs.map { store.pdfFile(it.id) }, out)
                store.thumbFile(docs.first().id).takeIf { it.exists() }?.copyTo(store.thumbFile(id), overwrite = true)
            }
            val first = docs.first()
            val entry = DocEntry(
                id = id,
                title = uniqueTitle(title.ifBlank { first.title + " (unido)" }),
                category = first.category,
                folderId = first.folderId,
                createdAt = System.currentTimeMillis(),
                docDateEpochDay = first.docDateEpochDay,
                pageCount = docs.sumOf { it.pageCount },
                sizeBytes = out.length(),
                text = docs.joinToString("\n") { it.text }.take(PdfTools.MAX_TEXT_CHARS),
                amount = null,
                issuer = first.issuer,
            )
            store.add(entry)
            _work.value = Work.Finished(id, "Documentos unidos")
        }
    }

    /** Applies a page edit in place: new order (dropped pages are deleted) and rotations. */
    fun editPages(id: String, order: List<Int>, rotations: Map<Int, Int>) {
        launchJob {
            _work.value = Work.Running("Guardando páginas", 0, 1)
            val src = store.pdfFile(id)
            val tmp = File(workDir, "editado.pdf")
            withContext(Dispatchers.IO) {
                PdfTools.rebuild(src, order, rotations, tmp)
                tmp.copyTo(src, overwrite = true)
                refreshThumb(id)
            }
            val text = withContext(Dispatchers.IO) { runCatching { PdfTools.extractText(src) }.getOrNull() }
            store.update(id) {
                it.copy(pageCount = order.size, sizeBytes = src.length(), text = text?.takeIf { t -> t.isNotBlank() } ?: it.text)
            }
            _work.value = Work.Finished(id, "Páginas actualizadas")
        }
    }

    /** Copies the chosen pages into a brand-new document. */
    fun extractPages(id: String, pages: List<Int>, title: String) {
        launchJob {
            _work.value = Work.Running("Separando páginas", 0, 1)
            val source = store.doc(id) ?: throw UserFacingException("El documento ya no existe.")
            val newId = store.newId()
            val out = store.pdfFile(newId)
            withContext(Dispatchers.IO) {
                PdfTools.rebuild(store.pdfFile(id), pages, emptyMap(), out)
                refreshThumb(newId)
            }
            val text = withContext(Dispatchers.IO) { runCatching { PdfTools.extractText(out) }.getOrDefault("") }
            store.add(
                source.copy(
                    id = newId,
                    title = uniqueTitle(title.ifBlank { source.title + " (págs.)" }),
                    createdAt = System.currentTimeMillis(),
                    pageCount = pages.size,
                    sizeBytes = out.length(),
                    text = text,
                    favorite = false,
                ),
            )
            _work.value = Work.Finished(newId, "Nuevo documento creado")
        }
    }

    private fun refreshThumb(id: String) {
        runCatching {
            PdfTools.openRenderer(store.pdfFile(id)).use { r ->
                val bmp = PdfTools.renderPage(r, 0, 360)
                Images.writeJpeg(bmp, store.thumbFile(id), 80)
                bmp.recycle()
            }
        }.onFailure { Log.w(TAG, "Thumbnail failed", it) }
    }

    /** Writes the tax-return pack: one folder per category plus a summary. */
    fun exportRentaZip(target: Uri, year: Int, categories: Set<Category>) {
        launchJob {
            val docs = rentaDocs(year, categories)
            if (docs.isEmpty()) throw UserFacingException("No hay documentos de $year en esas carpetas.")
            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openOutputStream(target)
                    ?: throw UserFacingException("No se pudo crear el archivo.")
                ZipOutputStream(stream.buffered()).use { zip ->
                    val used = HashSet<String>()
                    docs.forEachIndexed { i, doc ->
                        _work.value = Work.Running("Añadiendo ${doc.title}", i, docs.size)
                        val folder = TextTools.safeFileName(doc.category.label)
                        var name = "$folder/${TextTools.safeFileName(doc.title)}.pdf"
                        var n = 2
                        while (!used.add(name)) name = "$folder/${TextTools.safeFileName(doc.title)} (${n++}).pdf"
                        zip.putNextEntry(ZipEntry(name))
                        store.pdfFile(doc.id).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("Resumen Renta $year.txt"))
                    zip.write(rentaSummary(year, docs).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            _work.value = Work.Finished("", "Paquete Renta $year exportado (${docs.size} documentos)")
        }
    }

    fun rentaDocs(year: Int, categories: Set<Category>): List<DocEntry> =
        store.library.value.docs
            .filter { it.category in categories && it.effectiveDate.year == year }
            .sortedWith(compareBy<DocEntry> { it.category.ordinal }.thenBy { it.effectiveDate })

    private fun rentaSummary(year: Int, docs: List<DocEntry>): String = buildString {
        appendLine("Paquete para la Declaración de la Renta · ejercicio $year")
        appendLine("Generado con Escáner limpio, sin conexión y en tu móvil.")
        appendLine()
        docs.groupBy { it.category }.forEach { (cat, list) ->
            appendLine("== ${cat.label} (${list.size}) ==")
            list.forEach { d ->
                val amount = d.amount?.let { " · %.2f €".format(java.util.Locale.forLanguageTag("es-ES"), it) } ?: ""
                appendLine("- ${DocumentAnalyzer.fullDateLabel(d.effectiveDate)} · ${d.title}$amount")
            }
            val total = list.mapNotNull { it.amount }.sum()
            if (total > 0) appendLine("  Total detectado: %.2f €".format(java.util.Locale.forLanguageTag("es-ES"), total))
            appendLine()
        }
        appendLine("Los importes se leen automáticamente del texto y pueden no ser exactos. Revísalos antes de presentar la declaración.")
    }

    companion object {
        private const val TAG = "DocumentService"
        const val MAX_PAGES = 100
        const val MAX_IMPORT_BYTES = 150L * 1024 * 1024
    }
}
