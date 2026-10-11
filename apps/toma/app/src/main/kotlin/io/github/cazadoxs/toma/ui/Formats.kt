package io.github.cazadoxs.toma.ui

import android.content.Context
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.core.DoseSlot
import io.github.cazadoxs.toma.core.Schedule
import io.github.cazadoxs.toma.core.ScheduleType
import io.github.cazadoxs.toma.data.Instruction
import io.github.cazadoxs.toma.data.MedForm
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object Formats {
    private val timeFormatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun time(slot: DoseSlot): String = timeFormatter.format(slot.time)
    fun time(t: LocalTime): String = timeFormatter.format(t)

    fun amount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.getDefault(), value).trimEnd('0').trimEnd(',', '.')

    fun schedule(context: Context, s: Schedule): String {
        val times = s.slots.sortedBy { it.minuteOfDay }.joinToString(", ") { time(it) }
        return when (s.type) {
            ScheduleType.DAILY -> context.getString(R.string.sched_daily_desc, times)
            ScheduleType.WEEKDAYS -> {
                val days = s.weekdays.sorted().joinToString(", ") {
                    DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }
                context.getString(R.string.sched_weekdays_desc, days, times)
            }
            ScheduleType.EVERY_N_DAYS -> context.getString(R.string.sched_every_n_desc, s.everyNDays, times)
            ScheduleType.INTERVAL_HOURS ->
                context.getString(R.string.sched_interval_desc, s.intervalHours, s.slots.firstOrNull()?.let(::time) ?: "")
            ScheduleType.AS_NEEDED -> context.getString(R.string.sched_as_needed)
        }
    }

    fun instruction(context: Context, i: Instruction): String? = when (i) {
        Instruction.NONE -> null
        Instruction.WITH_FOOD -> context.getString(R.string.instr_with_food)
        Instruction.EMPTY_STOMACH -> context.getString(R.string.instr_empty_stomach)
        Instruction.BEFORE_MEAL -> context.getString(R.string.instr_before_meal)
        Instruction.AFTER_MEAL -> context.getString(R.string.instr_after_meal)
        Instruction.BEDTIME -> context.getString(R.string.instr_bedtime)
        Instruction.WITH_WATER -> context.getString(R.string.instr_with_water)
    }

    fun form(context: Context, f: MedForm): String = context.getString(
        when (f) {
            MedForm.PILL -> R.string.form_pill
            MedForm.CAPSULE -> R.string.form_capsule
            MedForm.LIQUID -> R.string.form_liquid
            MedForm.DROPS -> R.string.form_drops
            MedForm.INJECTION -> R.string.form_injection
            MedForm.INHALER -> R.string.form_inhaler
            MedForm.CREAM -> R.string.form_cream
            MedForm.PATCH -> R.string.form_patch
            MedForm.SACHET -> R.string.form_sachet
            MedForm.OTHER -> R.string.form_other
        },
    )
}
