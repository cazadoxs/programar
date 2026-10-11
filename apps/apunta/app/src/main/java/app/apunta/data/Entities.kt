package app.apunta.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

object Status {
    const val NEW = 0           // recorded/imported, not transcribed yet
    const val QUEUED = 1
    const val RUNNING = 2
    const val DONE = 3
    const val FAILED = 4
    const val NEEDS_PRO = 5     // monthly free minutes used up; partial transcript kept
    const val CANCELLED = 6
    const val IMPORTING = 7

    fun isBusy(s: Int) = s == QUEUED || s == RUNNING || s == IMPORTING
}

object Source {
    const val RECORDED = "rec"
    const val IMPORTED = "import"
}

@Entity(tableName = "recordings", indices = [Index("createdAt")])
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    /** File name inside filesDir/recordings. Never a path supplied from outside. */
    val fileName: String,
    val source: String = Source.RECORDED,
    /** Requested language: "auto", "es", "ca", "en"... */
    val language: String = "auto",
    val translate: Boolean = false,
    val detectedLanguage: String? = null,
    val modelId: String? = null,
    val status: Int = Status.NEW,
    val progress: Float = 0f,
    /** Audio already transcribed, so an interrupted job resumes where it stopped. */
    val processedMs: Long = 0,
    val error: String? = null,
    val summary: String? = null,
    val summarySource: String? = null,
    /** Comma-separated millisecond positions the user marked while recording. */
    val bookmarks: String = "",
    val starred: Boolean = false,
    val notes: String = "",
) {
    fun bookmarkList(): List<Long> = bookmarks.split(',').mapNotNull { it.trim().toLongOrNull() }
}

@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(
        entity = Recording::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recordingId")],
)
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val edited: Boolean = false,
)

/** Full-text index over segment text; diacritics are folded so "camion" finds "camión". */
@Fts4(contentEntity = Segment::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "segments_fts")
data class SegmentFts(
    @ColumnInfo(name = "text") val text: String,
)

/** A search hit with its recording title, for the global search screen. */
data class SearchHit(
    val segmentId: Long,
    val recordingId: Long,
    val startMs: Long,
    val text: String,
    val title: String,
    val createdAt: Long,
)
