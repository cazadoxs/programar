package io.github.cazadoxs.toma.data

import androidx.room.TypeConverter
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.core.Schedule
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun scheduleToJson(schedule: Schedule): String = json.encodeToString(Schedule.serializer(), schedule)

    @TypeConverter
    fun jsonToSchedule(value: String): Schedule = json.decodeFromString(Schedule.serializer(), value)

    @TypeConverter
    fun statusToString(status: DoseStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): DoseStatus = DoseStatus.valueOf(value)

    @TypeConverter
    fun formToString(form: MedForm): String = form.name

    @TypeConverter
    fun stringToForm(value: String): MedForm = runCatching { MedForm.valueOf(value) }.getOrDefault(MedForm.OTHER)

    @TypeConverter
    fun instructionToString(value: Instruction): String = value.name

    @TypeConverter
    fun stringToInstruction(value: String): Instruction =
        runCatching { Instruction.valueOf(value) }.getOrDefault(Instruction.NONE)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
