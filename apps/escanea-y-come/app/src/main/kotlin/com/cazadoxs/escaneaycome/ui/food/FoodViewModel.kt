package com.cazadoxs.escaneaycome.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.gramsText
import com.cazadoxs.escaneaycome.data.EntryEntity
import com.cazadoxs.escaneaycome.data.FoodEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodViewModel(private val c: AppContainer, foodId: Long, private val entryId: Long) : ViewModel() {
    val food: StateFlow<FoodEntity?> = c.repo.food(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var entry: EntryEntity? = null
        private set

    fun loadEntry(onLoaded: (EntryEntity) -> Unit) {
        if (entryId <= 0) return
        viewModelScope.launch { c.repo.entry(entryId)?.let { entry = it; onLoaded(it) } }
    }

    val isEditing: Boolean get() = entryId > 0

    fun toggleFavourite() {
        val f = food.value ?: return
        viewModelScope.launch { c.repo.setFavourite(f.id, !f.favourite) }
    }

    fun save(grams: Double, meal: Meal, day: Long, onDone: (String) -> Unit) {
        val f = food.value ?: return
        viewModelScope.launch {
            if (isEditing) {
                c.repo.updateEntry(entryId, grams, meal)
                onDone("Actualizado")
            } else {
                c.repo.log(f, grams, meal, day)
                onDone("${f.name} · ${grams.gramsText()} g en ${meal.label.lowercase()}")
            }
        }
    }

    fun deleteEntry(onDone: (String) -> Unit) {
        if (!isEditing) return
        viewModelScope.launch {
            c.repo.deleteEntry(entryId)
            onDone("Borrado")
        }
    }
}
