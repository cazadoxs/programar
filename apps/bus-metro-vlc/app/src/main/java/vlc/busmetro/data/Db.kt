package vlc.busmetro.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// transit.db: everything derived from the open-data feeds. Rebuilt on sync,
// excluded from backups.
// ---------------------------------------------------------------------------

@Entity(tableName = "stops", indices = [Index("network"), Index("lat"), Index("lon")])
data class StopEntity(
    @PrimaryKey val key: String,
    val network: String,
    val code: String,
    val name: String,
    val searchName: String,
    val lat: Double,
    val lon: Double,
    /** Distinct line names served, comma-separated, in display order. */
    val lines: String,
    val wheelchair: Boolean,
)

@Entity(tableName = "routes", primaryKeys = ["network", "idx"])
data class RouteEntity(
    val network: String,
    val idx: Int,
    val routeId: String,
    val shortName: String,
    val longName: String,
    val color: Int?,
    val textColor: Int?,
    val type: Int,
)

@Entity(tableName = "services", primaryKeys = ["network", "idx"])
data class ServiceEntity(
    val network: String,
    val idx: Int,
    val mask: Int,
    val start: Int,
    val end: Int,
    val added: String,
    val removed: String,
    val trips: Int,
)

@Entity(tableName = "headsigns", primaryKeys = ["network", "idx"])
data class HeadsignEntity(val network: String, val idx: Int, val text: String)

@Entity(tableName = "trips", primaryKeys = ["network", "idx"])
data class TripEntity(val network: String, val idx: Int, val tripId: String)

@Entity(tableName = "timetables")
data class TimetableEntity(@PrimaryKey val stopKey: String, val network: String, val blob: ByteArray) {
    override fun equals(other: Any?) = other is TimetableEntity && other.stopKey == stopKey
    override fun hashCode() = stopKey.hashCode()
}

@Entity(tableName = "patterns", indices = [Index("network", "lineName")])
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val network: String,
    val routeIdx: Int,
    val lineName: String,
    val headsign: String,
    val stopKeys: String,
    val shape: String?,
)

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val network: String,
    val version: String,
    val validFrom: Int,
    val validTo: Int,
    val importedAt: Long,
    val source: String,
    val stops: Int,
)

data class LineRow(val network: String, val shortName: String, val longName: String, val color: Int?, val textColor: Int?)

@Dao
interface TransitDao {
    @Query("SELECT * FROM stops WHERE `key` = :key")
    suspend fun stop(key: String): StopEntity?

    @Query("SELECT * FROM stops WHERE `key` IN (:keys)")
    suspend fun stops(keys: List<String>): List<StopEntity>

    @Query("SELECT * FROM stops")
    suspend fun allStops(): List<StopEntity>

    @Query("SELECT * FROM stops WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon")
    suspend fun stopsIn(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<StopEntity>

    @Query("SELECT * FROM stops WHERE code = :q OR searchName LIKE :like ORDER BY (code = :q) DESC, length(name) LIMIT :limit")
    suspend fun searchStops(q: String, like: String, limit: Int): List<StopEntity>

    @Query("SELECT * FROM routes WHERE network = :network")
    suspend fun routes(network: String): List<RouteEntity>

    @Query("SELECT network, shortName, MIN(longName) AS longName, MAX(color) AS color, MAX(textColor) AS textColor FROM routes GROUP BY network, shortName")
    suspend fun lines(): List<LineRow>

    @Query("SELECT * FROM services WHERE network = :network ORDER BY idx")
    suspend fun services(network: String): List<ServiceEntity>

    @Query("SELECT * FROM headsigns WHERE network = :network ORDER BY idx")
    suspend fun headsigns(network: String): List<HeadsignEntity>

    @Query("SELECT * FROM trips WHERE network = :network ORDER BY idx")
    suspend fun trips(network: String): List<TripEntity>

    @Query("SELECT blob FROM timetables WHERE stopKey = :key")
    suspend fun timetable(key: String): ByteArray?

    @Query("SELECT * FROM patterns WHERE network = :network AND lineName = :line")
    suspend fun patterns(network: String, line: String): List<PatternEntity>

    @Query("SELECT * FROM feeds")
    fun feedsFlow(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds")
    suspend fun feeds(): List<FeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStops(rows: List<StopEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoutes(rows: List<RouteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertServices(rows: List<ServiceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHeadsigns(rows: List<HeadsignEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrips(rows: List<TripEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTimetables(rows: List<TimetableEntity>)
    @Insert suspend fun insertPatterns(rows: List<PatternEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFeed(row: FeedEntity)

    @Query("DELETE FROM stops WHERE network = :n") suspend fun clearStops(n: String)
    @Query("DELETE FROM routes WHERE network = :n") suspend fun clearRoutes(n: String)
    @Query("DELETE FROM services WHERE network = :n") suspend fun clearServices(n: String)
    @Query("DELETE FROM headsigns WHERE network = :n") suspend fun clearHeadsigns(n: String)
    @Query("DELETE FROM trips WHERE network = :n") suspend fun clearTrips(n: String)
    @Query("DELETE FROM timetables WHERE network = :n") suspend fun clearTimetables(n: String)
    @Query("DELETE FROM patterns WHERE network = :n") suspend fun clearPatterns(n: String)
}

@Database(
    entities = [StopEntity::class, RouteEntity::class, ServiceEntity::class, HeadsignEntity::class, TripEntity::class,
        TimetableEntity::class, PatternEntity::class, FeedEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TransitDb : RoomDatabase() {
    abstract fun dao(): TransitDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, TransitDb::class.java, "transit.db")
            // Derived data: on a schema change simply rebuild it from the feeds.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}

// ---------------------------------------------------------------------------
// user.db: the person's own data (favourites, alerts read). Backed up.
// ---------------------------------------------------------------------------

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val key: String,
    val nickname: String?,
    val position: Int,
    /** Optional comma-separated line names to show; empty = all lines. */
    val lineFilter: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val network: String,
    val title: String,
    val body: String,
    val routeKeys: String,
    val stopKeys: String,
    val start: Long?,
    val end: Long?,
    val url: String?,
    val fetchedAt: Long,
    val notified: Boolean = false,
    val seen: Boolean = false,
)

@Entity(tableName = "bikes")
data class BikeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val bikes: Int,
    val docks: Int,
    val total: Int,
    val open: Boolean,
    val updated: Long?,
    val fetchedAt: Long,
)

@Dao
interface UserDao {
    @Query("SELECT * FROM favorites ORDER BY position, addedAt")
    fun favoritesFlow(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position, addedAt")
    suspend fun favorites(): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE `key` = :key")
    fun isFavoriteFlow(key: String): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), 0) FROM favorites")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFavorite(f: FavoriteEntity)
    @Update suspend fun updateFavorites(f: List<FavoriteEntity>)
    @Query("DELETE FROM favorites WHERE `key` = :key") suspend fun deleteFavorite(key: String)

    @Query("SELECT * FROM alerts WHERE `end` IS NULL OR `end` > :now ORDER BY COALESCE(start, fetchedAt) DESC")
    fun alertsFlow(now: Long): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts")
    suspend fun alerts(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAlerts(a: List<AlertEntity>)
    @Query("DELETE FROM alerts WHERE id NOT IN (:keep)") suspend fun pruneAlerts(keep: List<String>)
    @Query("UPDATE alerts SET seen = 1") suspend fun markAlertsSeen()
    @Query("UPDATE alerts SET notified = 1 WHERE id IN (:ids)") suspend fun markNotified(ids: List<String>)
    @Query("SELECT COUNT(*) FROM alerts WHERE seen = 0 AND (`end` IS NULL OR `end` > :now)") fun unseenCount(now: Long): Flow<Int>

    @Query("SELECT * FROM bikes") suspend fun bikes(): List<BikeEntity>
    @Query("SELECT * FROM bikes WHERE id = :id") suspend fun bike(id: String): BikeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBikes(b: List<BikeEntity>)
}

@Database(entities = [FavoriteEntity::class, AlertEntity::class, BikeEntity::class], version = 1, exportSchema = false)
abstract class UserDb : RoomDatabase() {
    abstract fun dao(): UserDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, UserDb::class.java, "user.db").build()
    }
}
