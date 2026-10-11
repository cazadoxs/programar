package com.cazadoxs.escanerlimpio.data

import com.cazadoxs.escanerlimpio.classify.Category
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class DocEntry(
    val id: String,
    val title: String,
    val category: Category,
    val folderId: String? = null,
    val createdAt: Long,
    val docDateEpochDay: Long? = null,
    val pageCount: Int,
    val sizeBytes: Long,
    val text: String = "",
    val amount: Double? = null,
    val issuer: String? = null,
    val favorite: Boolean = false,
) {
    val docDate: LocalDate? get() = docDateEpochDay?.let(LocalDate::ofEpochDay)

    /** The date that best represents the document: its own date, or when it was scanned. */
    val effectiveDate: LocalDate
        get() = docDate ?: Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
}

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class Library(
    val version: Int = 1,
    val docs: List<DocEntry> = emptyList(),
    val folders: List<Folder> = emptyList(),
)
