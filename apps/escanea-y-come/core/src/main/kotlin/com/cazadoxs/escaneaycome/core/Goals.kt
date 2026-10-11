package com.cazadoxs.escaneaycome.core

import kotlin.math.roundToInt

enum class Sex { MUJER, HOMBRE, OTRO }

enum class Activity(val factor: Double, val label: String) {
    SEDENTARIA(1.2, "Poco movimiento"),
    LIGERA(1.375, "Algo de ejercicio (1-3 días)"),
    MODERADA(1.55, "Ejercicio frecuente (3-5 días)"),
    ALTA(1.725, "Mucho ejercicio (6-7 días)"),
    MUY_ALTA(1.9, "Trabajo físico + deporte"),
}

enum class GoalType(val label: String) {
    PERDER("Perder peso poco a poco"),
    MANTENER("Mantener"),
    GANAR("Ganar masa"),
}

data class Profile(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: Activity,
    val goal: GoalType,
)

data class MacroTargets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

object Goals {
    const val MIN_ADULT_AGE = 18

    /** Mifflin-St Jeor resting energy. */
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return base + when (p.sex) {
            Sex.HOMBRE -> 5.0
            Sex.MUJER -> -161.0
            Sex.OTRO -> -78.0
        }
    }

    fun maintenance(p: Profile): Double = bmr(p) * p.activity.factor

    /**
     * Daily energy target. Deficits are capped at 15 % and never go below resting energy
     * or the usual safe floors, and minors only get a maintenance target.
     */
    fun dailyKcal(p: Profile): Int {
        val tdee = maintenance(p)
        val goal = if (p.age < MIN_ADULT_AGE && p.goal == GoalType.PERDER) GoalType.MANTENER else p.goal
        val raw = when (goal) {
            GoalType.PERDER -> tdee * 0.85
            GoalType.MANTENER -> tdee
            GoalType.GANAR -> tdee * 1.10
        }
        val floor = maxOf(bmr(p), if (p.sex == Sex.HOMBRE) 1500.0 else 1200.0)
        val safe = if (goal == GoalType.PERDER) maxOf(raw, floor) else raw
        return roundTo(safe, 10)
    }

    /** Splits [kcal] into grams. Percentages are normalized so they always add up to 100. */
    fun macros(kcal: Int, proteinPct: Int, carbsPct: Int, fatPct: Int): MacroTargets {
        val total = (proteinPct + carbsPct + fatPct).takeIf { it > 0 } ?: 100
        val p = kcal * proteinPct / total.toDouble() / 4
        val c = kcal * carbsPct / total.toDouble() / 4
        val f = kcal * fatPct / total.toDouble() / 9
        return MacroTargets(kcal, p.roundToInt(), c.roundToInt(), f.roundToInt())
    }

    fun isPlausible(p: Profile): Boolean =
        p.age in 13..110 && p.heightCm in 100.0..250.0 && p.weightKg in 30.0..350.0

    private fun roundTo(v: Double, step: Int) = ((v / step).roundToInt() * step)
}
