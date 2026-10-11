package com.cazadoxs.escaneaycome.core

import kotlin.math.roundToInt

data class DayTotals(val day: Long, val totals: Totals)

/** A logged item reduced to what the stats need. */
data class StatItem(val day: Long, val name: String, val totals: Totals)

data class Averages(val days: Int, val loggedDays: Int, val average: Totals)

data class FoodRank(val name: String, val value: Double, val count: Int)

object Stats {
    /** One entry per day in [fromDay]..[toDay], zero-filled. */
    fun byDay(items: List<StatItem>, fromDay: Long, toDay: Long): List<DayTotals> {
        val grouped = items.groupBy { it.day }
        return (fromDay..toDay).map { d ->
            DayTotals(d, grouped[d].orEmpty().fold(Totals.ZERO) { acc, i -> acc + i.totals })
        }
    }

    /** Averages over days that have at least one item, so empty days don't drag the mean down. */
    fun averages(days: List<DayTotals>): Averages {
        val logged = days.filter { it.totals.kcal > 0 || it.totals.protein > 0 }
        if (logged.isEmpty()) return Averages(days.size, 0, Totals.ZERO)
        val sum = logged.fold(Totals.ZERO) { acc, d -> acc + d.totals }
        val n = logged.size.toDouble()
        return Averages(days.size, logged.size, Totals(sum.kcal / n, sum.protein / n, sum.carbs / n, sum.fat / n))
    }

    /** Consecutive days with something logged, ending today (or yesterday if today is still empty). */
    fun streak(loggedDays: Set<Long>, today: Long): Int {
        var d = if (today in loggedDays) today else today - 1
        var n = 0
        while (d in loggedDays) {
            n++; d--
        }
        return n
    }

    /** Foods that contributed the most of a nutrient. */
    fun topFoods(items: List<StatItem>, limit: Int, selector: (Totals) -> Double): List<FoodRank> =
        items.groupBy { it.name.trim().lowercase() }
            .map { (_, list) -> FoodRank(list.first().name, list.sumOf { selector(it.totals) }, list.size) }
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(limit)

    /** Percent of days within ±10 % of the target, for those days that have data. */
    fun adherence(days: List<DayTotals>, kcalGoal: Int): Int {
        val logged = days.filter { it.totals.kcal > 0 }
        if (logged.isEmpty() || kcalGoal <= 0) return 0
        val ok = logged.count { kotlin.math.abs(it.totals.kcal - kcalGoal) <= kcalGoal * 0.10 }
        return (ok * 100.0 / logged.size).roundToInt()
    }

    /** Simple moving average used to smooth the weight line. */
    fun movingAverage(values: List<Double>, window: Int): List<Double> {
        if (window <= 1) return values
        return values.indices.map { i ->
            val from = maxOf(0, i - window + 1)
            values.subList(from, i + 1).average()
        }
    }
}
