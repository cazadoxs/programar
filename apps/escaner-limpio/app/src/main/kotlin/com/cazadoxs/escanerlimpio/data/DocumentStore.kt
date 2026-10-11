package com.cazadoxs.escanerlimpio.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * The whole library lives in app-private storage: one JSON index plus one PDF
 * and one thumbnail per document. Nothing leaves the device and nothing is
 * readable by other apps.
 */
class DocumentStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val root = File(context.filesDir, "library")
    private val docsDir = File(root, "docs")
    private val thumbsDir = File(root, "thumbs")
    private val index = AtomicFile(File(root, "index.json"))

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            docsDir.mkdirs()
            thumbsDir.mkdirs()
            val lib = runCatching {
                if (index.baseFile.exists()) json.decodeFromString<Library>(index.readFully().decodeToString()) else Library()
            }.onFailure { Log.w(TAG, "Index unreadable, starting empty", it) }.getOrDefault(Library())
            // Drop entries with forged ids or missing files.
            _library.value = lib.copy(docs = lib.docs.filter { isValidId(it.id) && pdfFile(it.id).exists() })
            _loaded.value = true
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun pdfFile(id: String): File {
        require(isValidId(id)) { "Invalid document id" }
        return File(docsDir, "$id.pdf")
    }

    fun thumbFile(id: String): File {
        require(isValidId(id)) { "Invalid document id" }
        return File(thumbsDir, "$id.jpg")
    }

    fun doc(id: String): DocEntry? = _library.value.docs.firstOrNull { it.id == id }

    suspend fun add(entry: DocEntry) = mutate { it.copy(docs = listOf(entry) + it.docs) }

    suspend fun update(id: String, transform: (DocEntry) -> DocEntry) = mutate { lib ->
        lib.copy(docs = lib.docs.map { if (it.id == id) transform(it) else it })
    }

    suspend fun delete(ids: Collection<String>) {
        mutate { lib -> lib.copy(docs = lib.docs.filterNot { it.id in ids }) }
        withContext(Dispatchers.IO) {
            ids.filter(::isValidId).forEach {
                pdfFile(it).delete()
                thumbFile(it).delete()
            }
        }
    }

    suspend fun createFolder(name: String): Folder {
        val folder = Folder(newId(), name.trim().take(60), System.currentTimeMillis())
        mutate { it.copy(folders = it.folders + folder) }
        return folder
    }

    suspend fun renameFolder(id: String, name: String) = mutate { lib ->
        lib.copy(folders = lib.folders.map { if (it.id == id) it.copy(name = name.trim().take(60)) else it })
    }

    suspend fun deleteFolder(id: String) = mutate { lib ->
        lib.copy(
            folders = lib.folders.filterNot { it.id == id },
            docs = lib.docs.map { if (it.folderId == id) it.copy(folderId = null) else it },
        )
    }

    private suspend fun mutate(transform: (Library) -> Library) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_library.value)
            val stream = index.startWrite()
            try {
                stream.write(json.encodeToString(Library.serializer(), next).encodeToByteArray())
                index.finishWrite(stream)
            } catch (e: Exception) {
                index.failWrite(stream)
                throw e
            }
            _library.value = next
        }
    }

    companion object {
        private const val TAG = "DocumentStore"
        private val ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        fun isValidId(id: String) = ID.matches(id)
    }
}
