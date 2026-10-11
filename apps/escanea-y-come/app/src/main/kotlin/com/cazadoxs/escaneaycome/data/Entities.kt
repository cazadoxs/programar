package com.cazadoxs.escaneaycome.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cazadoxs.escaneaycome.core.Per100
import com.cazadoxs.escaneaycome.core.Totals

/** A product from Open Food Facts (cached) or created by the user. */
@Entity(
    tableName = "foods",
    indices = [Index(value = ["barcode"], unique = true), Index("name"), Index("lastUsedAt")],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val quantity: String? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugars: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
    val saturatedFat: Double? = null,
    val servingGrams: Double? = null,
    val servingLabel: String? = null,
    val nutriScore: String? = null,
    val nova: Int? = null,
    val imageUrl: String? = null,
    /** Comma separated allergen tags, e.g. "milk,gluten". */
    val allergens: String = "",
    val labels: String = "",
    val isCustom: Boolean = false,
    val favourite: Boolean = false,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    val fetchedAt: Long = 0,
) {
    val per100: Per100
        get() = Per100(kcal, protein, carbs, fat, sugars, fiber, salt, saturatedFat)

    val allergenList: List<String> get() = allergens.split(',').filter { it.isNotBlank() }
    val labelList: List<String> get() = labels.split(',').filter { it.isNotBlank() }

    /** Default portion: one serving when known, else 100 g. */
    val defaultGrams: Double get() = servingGrams ?: 100.0
}

/**
 * One thing eaten. Name and nutrition are copied at logging time so editing or
 * deleting a food never rewrites history.
 */
@Entity(tableName = "entries", indices = [Index("day"), Index("foodId")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val meal: String,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val createdAt: Long,
) {
    val totals: Totals get() = Totals(kcal, protein, carbs, fat)
}

@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long = 0,
)

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealId")],
)
data class SavedMealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val day: Long,
    val kg: Double,
)

@Entity(tableName = "water")
data class WaterEntity(
    @PrimaryKey val day: Long,
    val glasses: Int,
)

data class SavedMealWithItems(
    val meal: SavedMealEntity,
    val items: List<SavedMealItemEntity>,
) {
    val totals: Totals get() = items.fold(Totals.ZERO) { a, i -> a + Totals(i.kcal, i.protein, i.carbs, i.fat) }
}
