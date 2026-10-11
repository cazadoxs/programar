package com.cazadoxs.escaneaycome.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.OffProduct
import com.cazadoxs.escaneaycome.data.FoodEntity
import com.cazadoxs.escaneaycome.data.OffException
import com.cazadoxs.escaneaycome.data.SavedMealWithItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUi(
    val query: String = "",
    val local: List<FoodEntity> = emptyList(),
    val online: List<OffProduct> = emptyList(),
    val loadingOnline: Boolean = false,
    val onlineError: String? = null,
    val searchedOnline: Boolean = false,
)

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    val recent: StateFlow<List<FoodEntity>> = repo.recentFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favourites: StateFlow<List<FoodEntity>> = repo.favourites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val custom: StateFlow<List<FoodEntity>> = repo.customFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val meals: StateFlow<List<SavedMealWithItems>> = repo.savedMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null

    fun setQuery(q: String) {
        val query = q.take(80)
        _ui.value = _ui.value.copy(query = query, onlineError = null)
        job?.cancel()
        if (query.isBlank()) {
            _ui.value = SearchUi()
            return
        }
        job = viewModelScope.launch {
            delay(250)
            _ui.value = _ui.value.copy(local = repo.searchLocal(query))
            // Open Food Facts allows ~10 searches a minute, so wait for the user to stop typing.
            if (query.trim().length >= 3) {
                delay(650)
                searchOnline(query)
            }
        }
    }

    fun retryOnline() {
        val q = _ui.value.query
        job?.cancel()
        job = viewModelScope.launch { searchOnline(q) }
    }

    private suspend fun searchOnline(query: String) {
        _ui.value = _ui.value.copy(loadingOnline = true, onlineError = null)
        _ui.value = try {
            val results = repo.searchOnline(query)
            _ui.value.copy(online = results, loadingOnline = false, searchedOnline = true)
        } catch (e: OffException) {
            _ui.value.copy(loadingOnline = false, onlineError = e.message, searchedOnline = true)
        }
    }

    fun open(p: OffProduct, onId: (Long) -> Unit) = viewModelScope.launch { onId(repo.upsertOff(p)) }

    fun logMeal(id: Long, meal: Meal, day: Long, onDone: () -> Unit) = viewModelScope.launch {
        repo.logSavedMeal(id, meal, day)
        onDone()
    }

    fun deleteMeal(id: Long) = viewModelScope.launch { repo.deleteSavedMeal(id) }
}
