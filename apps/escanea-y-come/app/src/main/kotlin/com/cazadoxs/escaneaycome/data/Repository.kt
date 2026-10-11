package com.cazadoxs.escaneaycome.data

import android.content.Context
import androidx.room.withTransaction
import com.cazadoxs.escaneaycome.core.Barcode
import com.cazadoxs.escaneaycome.core.Csv
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.OffLookup
import com.cazadoxs.escaneaycome.core.OffProduct
import com.cazadoxs.escaneaycome.core.Per100
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

sealed interface ScanResult {
    data class Ready(val foodId: Long) : ScanResult
    data class Missing(val barcode: String, val name: String?) : ScanResult
    data class Invalid(val raw: String) : ScanResult
}

class Repository(
    private val db: AppDatabase,
    private val off: OffClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val foods = db.foods()
    private val entries = db.entries()
    private val saved = db.savedMeals()
    private val body = db.body()

    // ---- Foods --------------------------------------------------------------------------

    fun food(id: Long): Flow<FoodEntity?> = foods.observe(id)
    suspend fun foodNow(id: Long): FoodEntity? = foods.byId(id)
    fun recentFoods(): Flow<List<FoodEntity>> = foods.recent(30)
    fun favourites(): Flow<List<FoodEntity>> = foods.favourites()
    fun customFoods(): Flow<List<FoodEntity>> = foods.custom()

    suspend fun setFavourite(id: Long, fav: Boolean) = foods.setFavourite(id, fav)

    /**
     * Resolves a scanned code: local cache first (works offline and keeps user edits),
     * then Open Food Facts. Cached products are refreshed after [REFRESH_MS].
     */
    suspend fun resolveBarcode(raw: String): ScanResult {
        val code = Barcode.normalize(raw) ?: return ScanResult.Invalid(raw)
        val cached = foods.byBarcode(code)
        if (cached != null && (cached.isCustom || now() - cached.fetchedAt < REFRESH_MS)) {
            return ScanResult.Ready(cached.id)
        }
        val lookup = try {
            off.product(code)
        } catch (e: OffException) {
            if (cached != null) return ScanResult.Ready(cached.id)
            throw e
        }
        return when (lookup) {
            is OffLookup.Found -> ScanResult.Ready(upsertOff(lookup.product))
            is OffLookup.Incomplete -> cached?.let { ScanResult.Ready(it.id) } ?: ScanResult.Missing(code, lookup.name)
            OffLookup.NotFound -> cached?.let { ScanResult.Ready(it.id) } ?: ScanResult.Missing(code, null)
        }
    }

    /** Stores an Open Food Facts product, keeping the user's favourite flag and usage. */
    suspend fun upsertOff(p: OffProduct): Long {
        val existing = foods.byBarcode(p.barcode)
        val entity = FoodEntity(
            id = existing?.id ?: 0,
            barcode = p.barcode,
            name = p.name,
            brand = p.brand,
            quantity = p.quantity,
            kcal = p.per100.kcal,
            protein = p.per100.protein,
            carbs = p.per100.carbs,
            fat = p.per100.fat,
            sugars = p.per100.sugars,
            fiber = p.per100.fiber,
            salt = p.per100.salt,
            saturatedFat = p.per100.saturatedFat,
            servingGrams = p.servingGrams,
            servingLabel = p.servingLabel,
            nutriScore = p.nutriScore,
            nova = p.nova,
            imageUrl = p.imageUrl,
            allergens = p.allergens.joinToString(","),
            labels = p.labels.joinToString(","),
            isCustom = false,
            favourite = existing?.favourite ?: false,
            useCount = existing?.useCount ?: 0,
            lastUsedAt = existing?.lastUsedAt ?: 0,
            fetchedAt = now(),
        )
        if (existing != null) {
            // A food the user created by hand for this barcode wins over the database.
            if (existing.isCustom) return existing.id
            foods.update(entity)
            return existing.id
        }
        return foods.insert(entity)
    }

    suspend fun searchLocal(query: String): List<FoodEntity> {
        val q = query.trim().take(80)
        if (q.isEmpty()) return emptyList()
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return foods.search("%$escaped%")
    }

    suspend fun searchOnline(query: String): List<OffProduct> = off.search(query)

    suspend fun saveCustomFood(
        id: Long?,
        name: String,
        brand: String?,
        barcode: String?,
        per100: Per100,
        servingGrams: Double?,
    ): Long {
        val clean = name.trim().take(80)
        require(clean.isNotEmpty())
        val code = barcode?.let { Barcode.normalize(it) }
        val old = id?.let { foods.byId(it) } ?: code?.let { foods.byBarcode(it) }
        // The barcode column is unique: a code moved onto this food replaces whatever had it.
        code?.let { foods.byBarcode(it) }?.takeIf { it.id != old?.id }?.let { deleteFood(it.id) }
        val entity = FoodEntity(
            id = old?.id ?: 0,
            barcode = code,
            name = clean,
            brand = brand?.trim()?.take(60)?.ifEmpty { null },
            kcal = per100.kcal,
            protein = per100.protein,
            carbs = per100.carbs,
            fat = per100.fat,
            sugars = per100.sugars,
            fiber = per100.fiber,
            salt = per100.salt,
            saturatedFat = per100.saturatedFat,
            servingGrams = servingGrams?.takeIf { it > 0 && it <= 2000 },
            servingLabel = null,
            isCustom = true,
            favourite = old?.favourite ?: false,
            useCount = old?.useCount ?: 0,
            lastUsedAt = old?.lastUsedAt ?: 0,
            fetchedAt = now(),
        )
        return if (old != null) {
            foods.update(entity); old.id
        } else {
            foods.insert(entity)
        }
    }

    suspend fun deleteFood(id: Long) = db.withTransaction {
        entries.detachFood(id)
        foods.delete(id)
    }

    // ---- Diary --------------------------------------------------------------------------

    fun entriesFor(day: Long): Flow<List<EntryEntity>> = entries.forDay(day)
    fun entriesBetween(from: Long, to: Long): Flow<List<EntryEntity>> = entries.between(from, to)
    fun loggedDays(from: Long): Flow<Set<Long>> = entries.loggedDaysSince(from).map { it.toSet() }

    suspend fun log(food: FoodEntity, grams: Double, meal: Meal, day: Long): Long {
        val g = grams.coerceIn(0.0, MAX_GRAMS)
        val t = food.per100.forGrams(g)
        return db.withTransaction {
            foods.markUsed(food.id, now())
            entries.insert(
                EntryEntity(
                    day = day, meal = meal.name, foodId = food.id, name = food.name, brand = food.brand,
                    grams = g, kcal = t.kcal, protein = t.protein, carbs = t.carbs, fat = t.fat, createdAt = now(),
                ),
            )
        }
    }

    /** Quick add without a food: just energy and optional macros ("un café con leche ~ 60 kcal"). */
    suspend fun quickAdd(name: String, kcal: Double, protein: Double, carbs: Double, fat: Double, meal: Meal, day: Long) {
        entries.insert(
            EntryEntity(
                day = day, meal = meal.name, foodId = null, name = name.trim().take(80).ifEmpty { "Añadido rápido" },
                brand = null, grams = 0.0, kcal = kcal.coerceIn(0.0, 5000.0), protein = protein.coerceIn(0.0, 500.0),
                carbs = carbs.coerceIn(0.0, 500.0), fat = fat.coerceIn(0.0, 500.0), createdAt = now(),
            ),
        )
    }

    suspend fun entry(id: Long): EntryEntity? = entries.byId(id)

    /** Changes the amount of an entry, rescaling from its food when it still exists. */
    suspend fun updateEntry(id: Long, grams: Double, meal: Meal) {
        val e = entries.byId(id) ?: return
        val g = grams.coerceIn(0.0, MAX_GRAMS)
        val food = e.foodId?.let { foods.byId(it) }
        val updated = when {
            food != null -> {
                val t = food.per100.forGrams(g)
                e.copy(grams = g, meal = meal.name, kcal = t.kcal, protein = t.protein, carbs = t.carbs, fat = t.fat)
            }
            e.grams > 0 -> {
                val f = g / e.grams
                e.copy(grams = g, meal = meal.name, kcal = e.kcal * f, protein = e.protein * f, carbs = e.carbs * f, fat = e.fat * f)
            }
            else -> e.copy(meal = meal.name)
        }
        entries.update(updated)
    }

    suspend fun deleteEntry(id: Long) = entries.delete(id)

    suspend fun restoreEntry(e: EntryEntity) {
        entries.insert(e.copy(id = 0))
    }

    /** Copies a meal from one day to another, e.g. "lo mismo que ayer". Returns how many were copied. */
    suspend fun copyMeal(fromDay: Long, toDay: Long, meal: Meal): Int {
        val src = entries.forDayMeal(fromDay, meal.name)
        if (src.isEmpty()) return 0
        val t = now()
        entries.insertAll(src.mapIndexed { i, e -> e.copy(id = 0, day = toDay, createdAt = t + i) })
        return src.size
    }

    // ---- Saved meals --------------------------------------------------------------------

    fun savedMeals(): Flow<List<SavedMealWithItems>> = saved.all().map { list -> list.map { it.toModel() } }

    suspend fun saveMeal(name: String, day: Long, meal: Meal): Boolean {
        val src = entries.forDayMeal(day, meal.name)
        if (src.isEmpty()) return false
        saved.save(
            SavedMealEntity(name = name.trim().take(60).ifEmpty { meal.label }, createdAt = now()),
            src.map {
                SavedMealItemEntity(
                    mealId = 0, foodId = it.foodId, name = it.name, brand = it.brand, grams = it.grams,
                    kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat,
                )
            },
        )
        return true
    }

    suspend fun logSavedMeal(id: Long, meal: Meal, day: Long) {
        val m = saved.byId(id) ?: return
        val t = now()
        db.withTransaction {
            entries.insertAll(
                m.items.mapIndexed { i, it ->
                    EntryEntity(
                        day = day, meal = meal.name, foodId = it.foodId, name = it.name, brand = it.brand,
                        grams = it.grams, kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat,
                        createdAt = t + i,
                    )
                },
            )
            saved.markUsed(id, t)
        }
    }

    suspend fun deleteSavedMeal(id: Long) = saved.delete(id)

    // ---- Body ---------------------------------------------------------------------------

    fun weights(): Flow<List<WeightEntity>> = body.weights()
    suspend fun latestWeight(): WeightEntity? = body.latestWeight()
    suspend fun putWeight(day: Long, kg: Double) = body.putWeight(WeightEntity(day, kg.coerceIn(20.0, 400.0)))
    suspend fun deleteWeight(day: Long) = body.deleteWeight(day)
    fun water(day: Long): Flow<Int> = body.water(day).map { it ?: 0 }
    suspend fun setWater(day: Long, glasses: Int) = body.putWater(WaterEntity(day, glasses.coerceIn(0, 30)))

    // ---- Export / reset -----------------------------------------------------------------

    /** Writes the full diary as CSV into the app cache and returns the file. */
    suspend fun exportCsv(context: Context): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "escanea-y-come-${LocalDate.now()}.csv")
        file.bufferedWriter().use { w ->
            w.write(Csv.row(listOf("fecha", "comida", "alimento", "marca", "gramos", "kcal", "proteinas_g", "hidratos_g", "grasas_g")))
            w.write("\n")
            for (e in entries.all()) {
                w.write(
                    Csv.row(
                        listOf(
                            LocalDate.ofEpochDay(e.day).toString(), Meal.fromName(e.meal).label, e.name, e.brand.orEmpty(),
                            Csv.number(e.grams), Csv.number(e.kcal), Csv.number(e.protein), Csv.number(e.carbs), Csv.number(e.fat),
                        ),
                    ),
                )
                w.write("\n")
            }
        }
        file
    }

    suspend fun wipeAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    companion object {
        const val REFRESH_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_GRAMS = 5000.0
    }
}
