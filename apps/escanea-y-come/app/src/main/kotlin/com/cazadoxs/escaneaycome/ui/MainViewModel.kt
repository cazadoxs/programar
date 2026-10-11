package com.cazadoxs.escaneaycome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.data.OffException
import com.cazadoxs.escaneaycome.data.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Open(val foodId: Long) : LookupState
    data class Missing(val barcode: String, val name: String?) : LookupState
    data class Error(val message: String) : LookupState
}

/** App-wide state: the day being viewed and the barcode lookup in progress. */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _day = MutableStateFlow(LocalDate.now().toEpochDay())
    val day: StateFlow<Long> = _day.asStateFlow()

    private val _lookup = MutableStateFlow<LookupState>(LookupState.Idle)
    val lookup: StateFlow<LookupState> = _lookup.asStateFlow()

    fun setDay(day: Long) {
        _day.value = day.coerceAtMost(LocalDate.now().toEpochDay() + 1)
    }

    fun shiftDay(delta: Int) = setDay(_day.value + delta)

    fun lookup(raw: String) {
        if (_lookup.value == LookupState.Loading) return
        _lookup.value = LookupState.Loading
        viewModelScope.launch {
            _lookup.value = try {
                when (val r = container.repo.resolveBarcode(raw)) {
                    is ScanResult.Ready -> LookupState.Open(r.foodId)
                    is ScanResult.Missing -> LookupState.Missing(r.barcode, r.name)
                    is ScanResult.Invalid -> LookupState.Error("Ese código no parece de un producto. Revisa los números.")
                }
            } catch (e: OffException) {
                LookupState.Error(e.message ?: "Error de conexión")
            }
        }
    }

    fun consumeLookup() {
        _lookup.value = LookupState.Idle
    }
}
