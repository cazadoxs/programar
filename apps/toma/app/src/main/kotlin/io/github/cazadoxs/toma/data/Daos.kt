package io.github.cazadoxs.toma.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications")
    suspend fun all(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun active(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun byId(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE id = :id")
    fun observe(id: Long): Flow<MedicationEntity?>

    @Insert
    suspend fun insert(med: MedicationEntity): Long

    @Update
    suspend fun update(med: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE medications SET stock = :stock WHERE id = :id")
    suspend fun setStock(id: Long, stock: Double?)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    @Query("UPDATE medications SET lastRefillWarnDay = :day WHERE id = :id")
    suspend fun setLastRefillWarnDay(id: Long, day: Long)
}

@Dao
interface DoseDao {
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    fun observeBetween(from: Long, to: Long): Flow<List<DoseEntity>>

    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    suspend fun between(from: Long, to: Long): List<DoseEntity>

    @Query("SELECT * FROM doses WHERE medicationId = :medId AND scheduledAt = :at")
    suspend fun find(medId: Long, at: Long): DoseEntity?

    @Query("SELECT * FROM doses ORDER BY scheduledAt")
    suspend fun all(): List<DoseEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(dose: DoseEntity): Long

    @Upsert
    suspend fun upsert(dose: DoseEntity)

    @Delete
    suspend fun delete(dose: DoseEntity)

    @Query("DELETE FROM doses")
    suspend fun deleteAll()
}
