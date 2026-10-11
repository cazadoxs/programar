package com.cazadoxs.escaneaycome.core

import kotlin.math.roundToInt

/** Nutrition values per 100 g (or 100 ml) of a food, as printed on EU labels. */
data class Per100(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugars: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
    val saturatedFat: Double? = null,
) {
    /** Totals for [grams] of this food. */
    fun forGrams(grams: Double): Totals {
        val f = grams.coerceAtLeast(0.0) / 100.0
        return Totals(kcal * f, protein * f, carbs * f, fat * f)
    }

    companion object {
        const val MAX_KCAL = 950.0
        const val MAX_GRAMS = 100.0

        /**
         * Builds sanitized values. Kilocalories fall back to kilojoules and then to the
         * Atwater estimate, and every value is clamped to what 100 g can physically hold.
         */
        fun sanitized(
            kcal: Double?,
            kj: Double?,
            protein: Double?,
            carbs: Double?,
            fat: Double?,
            sugars: Double? = null,
            fiber: Double? = null,
            salt: Double? = null,
            saturatedFat: Double? = null,
        ): Per100? {
            val p = clampGrams(protein) ?: 0.0
            val c = clampGrams(carbs) ?: 0.0
            val fa = clampGrams(fat) ?: 0.0
            val energy = when {
                kcal != null && kcal.isFinite() && kcal >= 0 -> kcal
                kj != null && kj.isFinite() && kj >= 0 -> kj / KJ_PER_KCAL
                protein != null || carbs != null || fat != null -> p * 4 + c * 4 + fa * 9
                else -> return null
            }
            return Per100(
                kcal = energy.coerceIn(0.0, MAX_KCAL),
                protein = p,
                carbs = c,
                fat = fa,
                sugars = clampGrams(sugars),
                fiber = clampGrams(fiber),
                salt = clampGrams(salt),
                saturatedFat = clampGrams(saturatedFat),
            )
        }

        private fun clampGrams(v: Double?): Double? =
            v?.takeIf { it.isFinite() }?.coerceIn(0.0, MAX_GRAMS)
    }
}

const val KJ_PER_KCAL = 4.184

/** Energy and macros for an amount actually eaten. */
data class Totals(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(o: Totals) = Totals(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)

    /** Share of energy coming from each macro, 0..1, using 4/4/9 kcal per gram. */
    fun energySplit(): MacroSplit {
        val p = protein * 4
        val c = carbs * 4
        val f = fat * 9
        val sum = p + c + f
        if (sum <= 0) return MacroSplit(0.0, 0.0, 0.0)
        return MacroSplit(p / sum, c / sum, f / sum)
    }

    companion object {
        val ZERO = Totals()
    }
}

data class MacroSplit(val protein: Double, val carbs: Double, val fat: Double)

fun Double.kcalText(): String = roundToInt().toString()

/** "12,5" style: Spanish decimal comma, one decimal, no trailing ",0". */
fun Double.gramsText(): String {
    val r = (this * 10).roundToInt() / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString().replace('.', ',')
}

/** Parses user input like "12,5" or "12.5"; null when not a finite non-negative number. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
