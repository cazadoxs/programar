package app.apunta.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY starred DESC, createdAt DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observe(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun withStatus(statuses: List<Int>): List<Recording>

    @Insert
    suspend fun insert(r: Recording): Long

    @Update
    suspend fun update(r: Recording)

    @Query("UPDATE recordings SET status = :status, progress = :progress, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int, progress: Float, error: String? = null)

    @Query("UPDATE recordings SET processedMs = :processedMs, progress = :progress WHERE id = :id")
    suspend fun setProcessed(id: Long, processedMs: Long, progress: Float)

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE recordings SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE recordings SET summary = :summary, summarySource = :source WHERE id = :id")
    suspend fun setSummary(id: Long, summary: String?, source: String?)

    @Query("UPDATE recordings SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String)

    @Query("UPDATE recordings SET detectedLanguage = :lang WHERE id = :id")
    suspend fun setDetectedLanguage(id: Long, lang: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)

    /** Recordings whose title matches, for search. */
    @Query("SELECT * FROM recordings WHERE title LIKE '%' || :q || '%' ESCAPE '\\' ORDER BY createdAt DESC LIMIT 50")
    suspend fun searchTitles(q: String): List<Recording>

    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs ASC, id ASC")
    fun observe(recordingId: Long): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs ASC, id ASC")
    suspend fun list(recordingId: Long): List<Segment>

    @Insert
    suspend fun insertAll(segments: List<Segment>)

    @Query("UPDATE segments SET text = :text, edited = 1 WHERE id = :id")
    suspend fun editText(id: Long, text: String)

    @Query("DELETE FROM segments WHERE recordingId = :recordingId")
    suspend fun deleteFor(recordingId: Long)

    @Query("DELETE FROM segments WHERE recordingId = :recordingId AND startMs >= :fromMs")
    suspend fun deleteFrom(recordingId: Long, fromMs: Long)

    @Query(
        """
        SELECT s.id AS segmentId, s.recordingId AS recordingId, s.startMs AS startMs, s.text AS text,
               r.title AS title, r.createdAt AS createdAt
        FROM segments_fts
        JOIN segments s ON s.id = segments_fts.rowid
        JOIN recordings r ON r.id = s.recordingId
        WHERE segments_fts MATCH :match
        ORDER BY r.createdAt DESC, s.startMs ASC
        LIMIT 200
        """
    )
    suspend fun search(match: String): List<SearchHit>

    @Query("SELECT SUM(LENGTH(text)) FROM segments WHERE recordingId = :recordingId")
    suspend fun textLength(recordingId: Long): Long?
}

@Database(entities = [Recording::class, Segment::class, SegmentFts::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordings(): RecordingDao
    abstract fun segments(): SegmentDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "apunta.db").build()
    }
}
