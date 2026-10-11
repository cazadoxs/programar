package io.github.cazadoxs.toma.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.core.Schedule

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
)

/** Pharmaceutical form, used for the icon and wording only. */
enum class MedForm { PILL, CAPSULE, LIQUID, DROPS, INJECTION, INHALER, CREAM, PATCH, SACHET, OTHER }

/** Optional reminder printed on the alarm. Toma never suggests these: the user picks them. */
enum class Instruction { NONE, WITH_FOOD, EMPTY_STOMACH, BEFORE_MEAL, AFTER_MEAL, BEDTIME, WITH_WATER }

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    /** The user's own words for one dose, e.g. "1 comprimido de 500 mg". */
    val doseLabel: String,
    val form: MedForm,
    val colorIndex: Int,
    val notes: String,
    val instruction: Instruction,
    val schedule: Schedule,
    /** Units left, or null if the user doesn't track stock. */
    val stock: Double?,
    val refillWarnDays: Int,
    val active: Boolean,
    val createdAt: Long,
    /** Epoch day of the last refill warning, so it is shown at most once a day. */
    val lastRefillWarnDay: Long? = null,
)

@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicationId", "scheduledAt"], unique = true), Index("scheduledAt")],
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** Expected time of the dose; for as-needed doses, the time it was taken. */
    val scheduledAt: Long,
    val status: DoseStatus,
    val loggedAt: Long,
    val amount: Double,
    /** True when this record took units from the stock, so undoing it can give them back. */
    val countedInStock: Boolean = false,
)
