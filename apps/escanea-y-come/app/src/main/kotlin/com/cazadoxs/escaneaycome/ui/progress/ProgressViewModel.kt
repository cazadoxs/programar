package com.cazadoxs.escaneaycome.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.core.Averages
import com.cazadoxs.escaneaycome.core.DayTotals
import com.cazadoxs.escaneaycome.core.FoodRank
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.StatItem
import com.cazadoxs.escaneaycome.core.Stats
import com.cazadoxs.escaneaycome.data.AppSettings
import com.cazadoxs.escaneaycome.data.WeightEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Range(val days: Int, val label: String, val plus: Boolean) {
    SEMANA(7, "7 días", false),
    MES(30, "30 días", true),
    TRIMESTRE(90, "90 días", true),
}

data class ProgressState(
    val range: Range = Range.SEMANA,
    val settings: AppSettings = AppSettings(),
    val days: List<DayTotals> = emptyList(),
    val averages: Averages = Averages(0, 0, com.cazadoxs.escaneaycome.core.Totals.ZERO),
    val adherence: Int = 0,
    val topProtein: List<FoodRank> = emptyList(),
    val topKcal: List<FoodRank> = emptyList(),
    val perMeal: Map<Meal, Double> = emptyMap(),
    val weights: List<WeightEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(Range.SEMANA)

    val state: StateFlow<ProgressState> = range.flatMapLatest { r ->
        val today = LocalDate.now().toEpochDay()
        val from = today - r.days + 1
        combine(c.repo.entriesBetween(from, today), c.repo.weights(), c.settings.settings) { entries, weights, settings ->
            val items = entries.map { StatItem(it.day, it.name, it.totals) }
            val days = Stats.byDay(items, from, today)
            val totalKcal = entries.sumOf { it.kcal }
            ProgressState(
                range = r,
                settings = settings,
                days = days,
                averages = Stats.averages(days),
                adherence = Stats.adherence(days, settings.kcalGoal),
                topProtein = Stats.topFoods(items, 5) { it.protein },
                topKcal = Stats.topFoods(items, 5) { it.kcal },
                perMeal = if (totalKcal > 0) {
                    entries.groupBy { Meal.fromName(it.meal) }.mapValues { (_, l) -> l.sumOf { it.kcal } / totalKcal }
                } else {
                    emptyMap()
                },
                weights = weights.filter { it.day >= today - maxOf(r.days, 30) + 1 },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun setRange(r: Range) {
        range.value = r
    }

    fun logWeight(kg: Double) = viewModelScope.launch {
        c.repo.putWeight(LocalDate.now().toEpochDay(), kg)
        c.settings.update { it.copy(weightKg = kg) }
    }

    fun deleteWeight(day: Long) = viewModelScope.launch { c.repo.deleteWeight(day) }
}
