package com.cazadoxs.escaneaycome.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.Stats
import com.cazadoxs.escaneaycome.core.Totals
import com.cazadoxs.escaneaycome.data.AppSettings
import com.cazadoxs.escaneaycome.data.EntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayState(
    val day: Long,
    val settings: AppSettings,
    val entries: Map<Meal, List<EntryEntity>> = emptyMap(),
    val totals: Totals = Totals.ZERO,
    val water: Int = 0,
    val streak: Int = 0,
) {
    fun mealTotals(meal: Meal): Totals = entries[meal].orEmpty().fold(Totals.ZERO) { a, e -> a + e.totals }
    val count: Int get() = entries.values.sumOf { it.size }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer, day: StateFlow<Long>) : ViewModel() {
    private val repo = c.repo

    val state: StateFlow<TodayState> = day.flatMapLatest { d ->
        combine(
            repo.entriesFor(d),
            repo.water(d),
            repo.loggedDays(LocalDate.now().toEpochDay() - 400),
            c.settings.settings,
        ) { entries, water, logged, settings ->
            TodayState(
                day = d,
                settings = settings,
                entries = entries.groupBy { Meal.fromName(it.meal) },
                totals = entries.fold(Totals.ZERO) { a, e -> a + e.totals },
                water = water,
                streak = Stats.streak(logged, LocalDate.now().toEpochDay()),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(day.value, c.settings.current))

    fun setWater(glasses: Int) = viewModelScope.launch { repo.setWater(state.value.day, glasses) }

    fun delete(entry: EntryEntity) = viewModelScope.launch { repo.deleteEntry(entry.id) }

    fun restore(entry: EntryEntity) = viewModelScope.launch { repo.restoreEntry(entry) }

    fun copyFromYesterday(meal: Meal, onResult: (Int) -> Unit) = viewModelScope.launch {
        val d = state.value.day
        onResult(repo.copyMeal(d - 1, d, meal))
    }

    fun saveMeal(name: String, meal: Meal, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repo.saveMeal(name, state.value.day, meal))
    }

    fun quickAdd(name: String, kcal: Double, p: Double, carbs: Double, f: Double, meal: Meal) = viewModelScope.launch {
        repo.quickAdd(name, kcal, p, carbs, f, meal, state.value.day)
    }
}
