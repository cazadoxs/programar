package com.cazadoxs.escaneaycome.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(food: FoodEntity): Long

    @Update
    suspend fun update(food: FoodEntity)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM foods WHERE useCount > 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE favourite = 1 ORDER BY name COLLATE NOCASE")
    fun favourites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE isCustom = 1 ORDER BY name COLLATE NOCASE")
    fun custom(): Flow<List<FoodEntity>>

    /** [pattern] is built by the repository with LIKE wildcards escaped. */
    @Query(
        "SELECT * FROM foods WHERE name LIKE :pattern ESCAPE '\\' OR brand LIKE :pattern ESCAPE '\\' " +
            "ORDER BY useCount DESC, name COLLATE NOCASE LIMIT 40",
    )
    suspend fun search(pattern: String): List<FoodEntity>

    @Query("UPDATE foods SET favourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: Long, fav: Boolean)

    @Query("UPDATE foods SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE day = :day ORDER BY createdAt")
    fun forDay(day: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE day = :day AND meal = :meal ORDER BY createdAt")
    suspend fun forDayMeal(day: Long, meal: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE day BETWEEN :from AND :to ORDER BY day, createdAt")
    fun between(from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries ORDER BY day, createdAt")
    suspend fun all(): List<EntryEntity>

    @Query("SELECT DISTINCT day FROM entries WHERE day >= :from")
    fun loggedDaysSince(from: Long): Flow<List<Long>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun byId(id: Long): EntryEntity?

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("UPDATE entries SET foodId = NULL WHERE foodId = :foodId")
    suspend fun detachFood(foodId: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SavedMealDao {
    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY lastUsedAt DESC, name COLLATE NOCASE")
    fun all(): Flow<List<SavedMealWithItemsRelation>>

    @Transaction
    @Query("SELECT * FROM saved_meals WHERE id = :id")
    suspend fun byId(id: Long): SavedMealWithItemsRelation?

    @Insert
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItemEntity>)

    @Query("UPDATE saved_meals SET lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun save(meal: SavedMealEntity, items: List<SavedMealItemEntity>): Long {
        val id = insertMeal(meal)
        insertItems(items.map { it.copy(id = 0, mealId = id) })
        return id
    }
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM weights ORDER BY day")
    fun weights(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY day DESC LIMIT 1")
    suspend fun latestWeight(): WeightEntity?

    @Upsert
    suspend fun putWeight(w: WeightEntity)

    @Query("DELETE FROM weights WHERE day = :day")
    suspend fun deleteWeight(day: Long)

    @Query("SELECT glasses FROM water WHERE day = :day")
    fun water(day: Long): Flow<Int?>

    @Upsert
    suspend fun putWater(w: WaterEntity)
}
