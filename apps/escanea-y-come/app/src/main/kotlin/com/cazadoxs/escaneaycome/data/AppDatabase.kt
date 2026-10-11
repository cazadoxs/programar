package com.cazadoxs.escaneaycome.data

import android.content.Context
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase

data class SavedMealWithItemsRelation(
    @Embedded val meal: SavedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<SavedMealItemEntity>,
) {
    fun toModel() = SavedMealWithItems(meal, items)
}

@Database(
    entities = [
        FoodEntity::class,
        EntryEntity::class,
        SavedMealEntity::class,
        SavedMealItemEntity::class,
        WeightEntity::class,
        WaterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun entries(): EntryDao
    abstract fun savedMeals(): SavedMealDao
    abstract fun body(): BodyDao

    companion object {
        const val NAME = "escanea.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
