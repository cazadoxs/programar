package com.cazadoxs.escaneaycome.core

import org.json.JSONArray
import org.json.JSONObject

/** A product as read from Open Food Facts, already sanitized for display and storage. */
data class OffProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val per100: Per100,
    val servingGrams: Double?,
    val servingLabel: String?,
    val nutriScore: String?,
    val nova: Int?,
    val imageUrl: String?,
    val allergens: List<String>,
    val labels: List<String>,
)

sealed interface OffLookup {
    data class Found(val product: OffProduct) : OffLookup
    /** Product exists in the database but has no usable nutrition table. */
    data class Incomplete(val barcode: String, val name: String?) : OffLookup
    data object NotFound : OffLookup
}

/** Parses Open Food Facts API v2 / search JSON. Never throws on malformed input. */
object OffParser {
    const val MAX_TEXT = 160

    /** Fields we request, to keep responses small. */
    val FIELDS = listOf(
        "code", "product_name", "product_name_es", "generic_name_es", "brands", "quantity",
        "serving_size", "serving_quantity", "nutriments", "nutriscore_grade", "nova_group",
        "image_front_small_url", "image_front_url", "allergens_tags", "labels_tags",
    ).joinToString(",")

    private val IMAGE_HOSTS = setOf("images.openfoodfacts.org", "static.openfoodfacts.org")

    fun parseProductResponse(json: String, requestedCode: String): OffLookup {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return OffLookup.NotFound
        if (root.optInt("status", 0) != 1) return OffLookup.NotFound
        val obj = root.optJSONObject("product") ?: return OffLookup.NotFound
        return parseProduct(obj, requestedCode)?.let { OffLookup.Found(it) }
            ?: OffLookup.Incomplete(requestedCode, productName(obj))
    }

    fun parseSearchResponse(json: String): List<OffProduct> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("products") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = Barcode.normalize(o.optString("code", "")) ?: return@mapNotNull null
            parseProduct(o, code)
        }.distinctBy { it.barcode }
    }

    fun parseProduct(o: JSONObject, fallbackCode: String): OffProduct? {
        val name = productName(o) ?: return null
        val n = o.optJSONObject("nutriments") ?: return null
        val per100 = Per100.sanitized(
            kcal = num(n, "energy-kcal_100g"),
            kj = num(n, "energy-kj_100g") ?: num(n, "energy_100g"),
            protein = num(n, "proteins_100g"),
            carbs = num(n, "carbohydrates_100g"),
            fat = num(n, "fat_100g"),
            sugars = num(n, "sugars_100g"),
            fiber = num(n, "fiber_100g"),
            salt = num(n, "salt_100g"),
            saturatedFat = num(n, "saturated-fat_100g"),
        ) ?: return null
        val code = Barcode.normalize(o.optString("code", "")) ?: fallbackCode
        val serving = num(o, "serving_quantity")?.takeIf { it > 0 && it <= 2000 }
        return OffProduct(
            barcode = code,
            name = name,
            brand = text(o, "brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            quantity = text(o, "quantity"),
            per100 = per100,
            servingGrams = serving,
            servingLabel = if (serving != null) text(o, "serving_size") else null,
            nutriScore = text(o, "nutriscore_grade")?.lowercase()?.takeIf { it in setOf("a", "b", "c", "d", "e") },
            nova = o.optInt("nova_group", 0).takeIf { it in 1..4 },
            imageUrl = safeImageUrl(text(o, "image_front_small_url") ?: text(o, "image_front_url")),
            allergens = tags(o.optJSONArray("allergens_tags")),
            labels = tags(o.optJSONArray("labels_tags")).take(8),
        )
    }

    private fun productName(o: JSONObject): String? =
        text(o, "product_name_es") ?: text(o, "product_name") ?: text(o, "generic_name_es")

    /** Only https images from Open Food Facts hosts are ever loaded. */
    fun safeImageUrl(url: String?): String? {
        if (url == null) return null
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        return if (uri.scheme == "https" && uri.host in IMAGE_HOSTS && uri.userInfo == null) url else null
    }

    private fun text(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        val s = o.optString(key, "").trim()
        if (s.isEmpty()) return null
        // Drop control characters and cap the length of anything shown on screen.
        return s.filter { !it.isISOControl() }.take(MAX_TEXT)
    }

    private fun num(o: JSONObject, key: String): Double? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = o.opt(key)
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return d?.takeIf { it.isFinite() && it >= 0 }
    }

    /** "en:gluten" -> "gluten", keeping only safe characters. */
    private fun tags(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i, "").substringAfter(':').replace('-', ' ').trim()
                .filter { it.isLetterOrDigit() || it == ' ' }
                .take(40)
                .takeIf { it.isNotEmpty() }
        }.distinct()
    }
}
