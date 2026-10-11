package io.github.cazadoxs.toma.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything a user owns in Toma, in a portable, versioned shape. */
@Serializable
data class BackupData(
    val format: Int = FORMAT,
    val createdAtMillis: Long,
    val profiles: List<BackupProfile>,
    val medications: List<BackupMedication>,
    val doses: List<BackupDose>,
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class BackupProfile(
    val id: Long,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
)

@Serializable
data class BackupMedication(
    val id: Long,
    val profileId: Long,
    val name: String,
    val doseLabel: String,
    val form: String,
    val colorIndex: Int,
    val notes: String,
    val instructions: String,
    val schedule: Schedule,
    val stock: Double? = null,
    val refillWarnDays: Int = 7,
    val active: Boolean = true,
    val createdAtMillis: Long,
)

@Serializable
data class BackupDose(
    val medicationId: Long,
    val scheduledAtMillis: Long,
    val status: DoseStatus,
    val loggedAtMillis: Long,
    val amount: Double,
)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    const val MAX_TEXT = 500
    const val MAX_PROFILES = 50
    const val MAX_MEDICATIONS = 2_000
    const val MAX_DOSES = 500_000

    fun encode(data: BackupData): ByteArray = json.encodeToString(BackupData.serializer(), data).toByteArray()

    /** Parses and validates a decrypted backup, rejecting content the app would never write. */
    fun decode(bytes: ByteArray): BackupData {
        val data = try {
            json.decodeFromString(BackupData.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.INVALID_CONTENT)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.INVALID_CONTENT)
        }
        if (data.format > BackupData.FORMAT) throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION)
        val valid = data.profiles.size in 1..MAX_PROFILES &&
            data.medications.size <= MAX_MEDICATIONS &&
            data.doses.size <= MAX_DOSES &&
            data.profiles.all { it.name.length <= MAX_TEXT && it.emoji.length <= 16 } &&
            data.medications.all { m ->
                listOf(m.name, m.doseLabel, m.form, m.notes, m.instructions).all { it.length <= MAX_TEXT } &&
                    m.schedule.isValid &&
                    (m.stock == null || m.stock in 0.0..100_000.0) &&
                    m.refillWarnDays in 0..365 &&
                    data.profiles.any { it.id == m.profileId }
            }
        if (!valid) throw BackupException(BackupException.Reason.INVALID_CONTENT)
        return data
    }
}

object Csv {
    /**
     * Escapes one field. Values that a spreadsheet would run as a formula are prefixed with a
     * quote so opening an export in Excel or Sheets can't execute anything.
     */
    fun field(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun row(vararg values: String): String = values.joinToString(",") { field(it) }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
