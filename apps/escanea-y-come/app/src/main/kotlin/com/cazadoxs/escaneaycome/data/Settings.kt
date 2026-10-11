package com.cazadoxs.escaneaycome.data

import android.content.Context
import android.content.SharedPreferences
import com.cazadoxs.escaneaycome.core.Activity
import com.cazadoxs.escaneaycome.core.GoalType
import com.cazadoxs.escaneaycome.core.Goals
import com.cazadoxs.escaneaycome.core.MacroTargets
import com.cazadoxs.escaneaycome.core.Profile
import com.cazadoxs.escaneaycome.core.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

enum class Accent(val label: String) { HUERTA("Huerta"), MAR("Mar"), CIRUELA("Ciruela"), NARANJA("Naranja") }

data class AppSettings(
    val onboarded: Boolean = false,
    val sex: Sex = Sex.OTRO,
    val birthYear: Int = 1990,
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0,
    val activity: Activity = Activity.LIGERA,
    val goal: GoalType = GoalType.MANTENER,
    /** 0 means "use the calculated target". */
    val customKcal: Int = 0,
    val proteinPct: Int = 25,
    val carbsPct: Int = 45,
    val fatPct: Int = 30,
    val calmMode: Boolean = false,
    val waterGoal: Int = 8,
    val accent: Accent = Accent.HUERTA,
    val plus: Boolean = false,
) {
    val age: Int get() = (LocalDate.now().year - birthYear).coerceIn(0, 120)

    val profile: Profile get() = Profile(sex, age, heightCm, weightKg, activity, goal)

    val isMinor: Boolean get() = age < Goals.MIN_ADULT_AGE

    val kcalGoal: Int get() = if (customKcal > 0) customKcal else Goals.dailyKcal(profile)

    val targets: MacroTargets get() = Goals.macros(kcalGoal, proteinPct, carbsPct, fatPct)
}

/** Small key-value settings, exposed as a StateFlow so Compose recomposes on change. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        write(next)
        state.value = next
    }

    private fun read() = AppSettings(
        onboarded = prefs.getBoolean("onboarded", false),
        sex = enumOr(prefs.getString("sex", null), Sex.OTRO),
        birthYear = prefs.getInt("birthYear", 1990),
        heightCm = prefs.getFloat("heightCm", 170f).toDouble(),
        weightKg = prefs.getFloat("weightKg", 70f).toDouble(),
        activity = enumOr(prefs.getString("activity", null), Activity.LIGERA),
        goal = enumOr(prefs.getString("goal", null), GoalType.MANTENER),
        customKcal = prefs.getInt("customKcal", 0),
        proteinPct = prefs.getInt("proteinPct", 25),
        carbsPct = prefs.getInt("carbsPct", 45),
        fatPct = prefs.getInt("fatPct", 30),
        calmMode = prefs.getBoolean("calmMode", false),
        waterGoal = prefs.getInt("waterGoal", 8),
        accent = enumOr(prefs.getString("accent", null), Accent.HUERTA),
        plus = prefs.getBoolean("plus", false),
    )

    private fun write(s: AppSettings) {
        prefs.edit()
            .putBoolean("onboarded", s.onboarded)
            .putString("sex", s.sex.name)
            .putInt("birthYear", s.birthYear)
            .putFloat("heightCm", s.heightCm.toFloat())
            .putFloat("weightKg", s.weightKg.toFloat())
            .putString("activity", s.activity.name)
            .putString("goal", s.goal.name)
            .putInt("customKcal", s.customKcal)
            .putInt("proteinPct", s.proteinPct)
            .putInt("carbsPct", s.carbsPct)
            .putInt("fatPct", s.fatPct)
            .putBoolean("calmMode", s.calmMode)
            .putInt("waterGoal", s.waterGoal)
            .putString("accent", s.accent.name)
            .putBoolean("plus", s.plus)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        state.value = AppSettings()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        const val FILE = "ajustes"
    }
}
