package io.github.cazadoxs.toma.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cazadoxs.toma.alarm.DoseActions
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.AdherenceStats
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.data.DayDose
import io.github.cazadoxs.toma.data.DueDose
import io.github.cazadoxs.toma.data.MedicationEntity
import io.github.cazadoxs.toma.data.ProfileEntity
import io.github.cazadoxs.toma.settings.Prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class TodayState(
    val doses: List<DayDose> = emptyList(),
    val next: List<DueDose> = emptyList(),
    val streak: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val repository = c.repository
    val prefs: StateFlow<Prefs> = c.settings.state

    val profiles: StateFlow<List<ProfileEntity>> =
        repository.observeProfiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val medications: StateFlow<List<MedicationEntity>> =
        repository.observeMedications().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Selected day on the Today screen. */
    val day = MutableStateFlow(LocalDate.now())

    private val clock = flow {
        while (true) {
            emit(Instant.now())
            kotlinx.coroutines.delay(30_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<TodayState> = combine(day, prefs, clock) { d, p, _ -> d to p.activeProfileId }
        .flatMapLatest { (date, profileId) ->
            val filter = profileId.takeIf { it != Prefs.ALL_PROFILES }
            repository.observeDay(date, filter) { Instant.now() }.let { doses ->
                combine(doses, clock) { list, now ->
                    TodayState(
                        doses = list,
                        next = if (date == LocalDate.now()) repository.nextDoseGroup(now) else emptyList(),
                        streak = repository.streak(filter, now),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun selectProfile(id: Long) = c.settings.update { it.copy(activeProfileId = id) }

    fun record(dose: DayDose, status: DoseStatus) = viewModelScope.launch {
        DoseActions.record(getApplication(), dose.med.id, dose.at, dose.amount, status)
    }

    fun undo(dose: DayDose) = viewModelScope.launch { DoseActions.undo(getApplication(), dose.med.id, dose.at) }

    fun takeAsNeeded(med: MedicationEntity, amount: Double) = viewModelScope.launch {
        DoseActions.takeAsNeeded(getApplication(), med.id, amount)
    }

    fun saveMedication(med: MedicationEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        repository.saveMedication(med)
        c.scheduler.rescheduleAll()
        onDone()
    }

    fun deleteMedication(id: Long) = viewModelScope.launch {
        repository.deleteMedication(id)
        c.scheduler.rescheduleAll()
    }

    fun setActive(med: MedicationEntity, active: Boolean) = viewModelScope.launch {
        repository.setActive(med, active)
        c.scheduler.rescheduleAll()
    }

    fun addStock(med: MedicationEntity, units: Double) = viewModelScope.launch {
        repository.setStock(med.id, (med.stock ?: 0.0) + units)
    }

    fun addProfile(name: String, emoji: String, color: Int) = viewModelScope.launch { repository.addProfile(name, emoji, color) }
    fun updateProfile(p: ProfileEntity) = viewModelScope.launch { repository.updateProfile(p) }

    fun deleteProfile(p: ProfileEntity, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = repository.deleteProfile(p)
        if (ok) {
            if (c.settings.current.activeProfileId == p.id) selectProfile(Prefs.ALL_PROFILES)
            c.scheduler.rescheduleAll()
        }
        onResult(ok)
    }

    suspend fun adherence(days: Int): AdherenceStats {
        val p = prefs.value.activeProfileId.takeIf { it != Prefs.ALL_PROFILES }
        val now = Instant.now()
        val from = LocalDate.now().minusDays(days - 1L).atStartOfDay(repository.zone).toInstant()
        return repository.adherence(p, from, now, now)
    }

    fun finishOnboarding(name: String) = viewModelScope.launch {
        if (repository.profiles().isEmpty()) repository.addProfile(name.ifBlank { "Yo" }, "🙂", 1)
        c.settings.update { it.copy(onboardingDone = true) }
    }

    fun update(block: (Prefs) -> Prefs) = c.settings.update(block)
}
