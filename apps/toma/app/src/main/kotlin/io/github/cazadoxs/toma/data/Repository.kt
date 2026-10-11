package io.github.cazadoxs.toma.data

import androidx.room.withTransaction
import io.github.cazadoxs.toma.core.Adherence
import io.github.cazadoxs.toma.core.AdherenceStats
import io.github.cazadoxs.toma.core.DoseRecord
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.core.ExpectedDose
import io.github.cazadoxs.toma.core.ScheduleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where a dose stands right now. */
enum class DoseState { UPCOMING, DUE, TAKEN, SKIPPED, MISSED }

data class DayDose(
    val med: MedicationEntity,
    val at: Instant,
    val amount: Double,
    val record: DoseEntity?,
    val state: DoseState,
)

data class DueDose(val med: MedicationEntity, val at: Instant, val amount: Double)

class Repository(private val db: TomaDatabase) {
    private val profiles = db.profiles()
    private val meds = db.medications()
    private val doses = db.doses()

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun observeProfiles(): Flow<List<ProfileEntity>> = profiles.observeAll()
    fun observeMedications(): Flow<List<MedicationEntity>> = meds.observeAll()
    fun observeMedication(id: Long): Flow<MedicationEntity?> = meds.observe(id)

    suspend fun profiles(): List<ProfileEntity> = profiles.all()
    suspend fun profile(id: Long): ProfileEntity? = profiles.byId(id)
    suspend fun medication(id: Long): MedicationEntity? = meds.byId(id)
    suspend fun activeMedications(): List<MedicationEntity> = meds.active()

    suspend fun addProfile(name: String, emoji: String, colorIndex: Int): Long =
        profiles.insert(ProfileEntity(name = name.trim().take(MAX_NAME), emoji = emoji, colorIndex = colorIndex))

    suspend fun updateProfile(profile: ProfileEntity) = profiles.update(profile.copy(name = profile.name.trim().take(MAX_NAME)))

    /** Deletes a profile and, through the foreign keys, its medications and history. Keeps at least one. */
    suspend fun deleteProfile(profile: ProfileEntity): Boolean {
        if (profiles.count() <= 1) return false
        profiles.delete(profile)
        return true
    }

    suspend fun saveMedication(med: MedicationEntity): Long {
        val clean = med.copy(
            name = med.name.trim().take(MAX_NAME),
            doseLabel = med.doseLabel.trim().take(MAX_NAME),
            notes = med.notes.trim().take(MAX_NOTES),
        )
        require(clean.name.isNotEmpty() && clean.schedule.isValid)
        return if (clean.id == 0L) meds.insert(clean) else clean.id.also { meds.update(clean) }
    }

    suspend fun deleteMedication(id: Long) = meds.delete(id)

    suspend fun setActive(med: MedicationEntity, active: Boolean) = meds.update(med.copy(active = active))

    suspend fun setStock(medId: Long, stock: Double?) = meds.setStock(medId, stock?.coerceIn(0.0, MAX_STOCK))

    /**
     * Records what happened with one dose, keeping the stock in step: taking a dose subtracts
     * it once, and changing a taken dose to anything else gives the units back.
     */
    suspend fun logDose(medId: Long, at: Instant, status: DoseStatus, amount: Double, now: Instant = Instant.now()) {
        db.withTransaction {
            val med = meds.byId(medId) ?: return@withTransaction
            val existing = doses.find(medId, at.toEpochMilli())
            var stock = med.stock
            var counted = existing?.countedInStock ?: false
            if (counted && status != DoseStatus.TAKEN) {
                stock = stock?.plus(existing!!.amount)
                counted = false
            }
            if (status == DoseStatus.TAKEN && !counted && stock != null) {
                stock = (stock - amount).coerceAtLeast(0.0)
                counted = true
            }
            if (stock != med.stock) meds.setStock(medId, stock)
            doses.upsert(
                DoseEntity(
                    id = existing?.id ?: 0,
                    medicationId = medId,
                    scheduledAt = at.toEpochMilli(),
                    status = status,
                    loggedAt = now.toEpochMilli(),
                    amount = amount,
                    countedInStock = counted,
                ),
            )
        }
    }

    /** Removes a record so the dose is pending again, returning taken units to the stock. */
    suspend fun undoDose(medId: Long, at: Instant) {
        db.withTransaction {
            val existing = doses.find(medId, at.toEpochMilli()) ?: return@withTransaction
            if (existing.countedInStock) {
                val med = meds.byId(medId)
                med?.stock?.let { meds.setStock(medId, it + existing.amount) }
            }
            doses.delete(existing)
        }
    }

    suspend fun logAsNeeded(medId: Long, amount: Double, now: Instant = Instant.now()) {
        // Truncate to the second so two taps in the same instant don't collide on the unique index.
        val at = Instant.ofEpochSecond(now.epochSecond)
        logDose(medId, at, DoseStatus.TAKEN, amount, now)
    }

    /** Every dose of [date] for the profile (or all profiles), with its current state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDay(date: LocalDate, profileId: Long?, nowProvider: () -> Instant): Flow<List<DayDose>> {
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        return combine(meds.observeAll(), doses.observeBetween(from.toEpochMilli(), to.toEpochMilli())) { medList, records ->
            buildDay(medList.filter { profileId == null || it.profileId == profileId }, records, from, to, nowProvider())
        }
    }

    private fun buildDay(
        medList: List<MedicationEntity>,
        records: List<DoseEntity>,
        from: Instant,
        to: Instant,
        now: Instant,
    ): List<DayDose> {
        val byKey = records.associateBy { it.medicationId to it.scheduledAt }
        val result = ArrayList<DayDose>()
        for (med in medList) {
            if (med.schedule.type == ScheduleType.AS_NEEDED) {
                records.filter { it.medicationId == med.id }.forEach {
                    result += DayDose(med, Instant.ofEpochMilli(it.scheduledAt), it.amount, it, stateOf(it))
                }
                continue
            }
            val scheduled = if (med.active) med.schedule.occurrences(from, to, zone) else emptyList()
            val seen = HashSet<Long>()
            for (occ in scheduled) {
                val record = byKey[med.id to occ.at.toEpochMilli()]
                seen += occ.at.toEpochMilli()
                val state = when {
                    record != null -> stateOf(record)
                    occ.at.isAfter(now) -> DoseState.UPCOMING
                    else -> DoseState.DUE
                }
                result += DayDose(med, occ.at, occ.amount, record, state)
            }
            // Keep history visible even if the schedule changed afterwards.
            records.filter { it.medicationId == med.id && it.scheduledAt !in seen }.forEach {
                result += DayDose(med, Instant.ofEpochMilli(it.scheduledAt), it.amount, it, stateOf(it))
            }
        }
        return result.sortedWith(compareBy({ it.at }, { it.med.name }))
    }

    private fun stateOf(record: DoseEntity) = when (record.status) {
        DoseStatus.TAKEN -> DoseState.TAKEN
        DoseStatus.SKIPPED -> DoseState.SKIPPED
        DoseStatus.MISSED -> DoseState.MISSED
    }

    /** Scheduled doses up to [now] (plus a little slack) that have no record yet. */
    suspend fun dueDoses(now: Instant, lookBackMillis: Long = DUE_LOOKBACK_MILLIS): List<DueDose> {
        val from = now.minusMillis(lookBackMillis)
        val to = now.plusMillis(DUE_SLACK_MILLIS)
        val active = meds.active().filter { it.schedule.type != ScheduleType.AS_NEEDED }
        if (active.isEmpty()) return emptyList()
        val recorded = doses.between(from.toEpochMilli(), to.toEpochMilli())
            .map { it.medicationId to it.scheduledAt }.toSet()
        return active.flatMap { med ->
            val start = maxOf(from, Instant.ofEpochMilli(med.createdAt).minusMillis(DUE_SLACK_MILLIS))
            med.schedule.occurrences(start, to, zone)
                .filter { (med.id to it.at.toEpochMilli()) !in recorded }
                .map { DueDose(med, it.at, it.amount) }
        }.sortedBy { it.at }
    }

    /** Earliest dose strictly after [after] across all active medications. */
    suspend fun nextDose(after: Instant): DueDose? =
        meds.active().filter { it.schedule.type != ScheduleType.AS_NEEDED }.mapNotNull { med ->
            med.schedule.nextOccurrence(after, zone)?.let { DueDose(med, it.at, it.amount) }
        }.minByOrNull { it.at }

    /** All doses due at the same instant as the next one, for the widget and the Today header. */
    suspend fun nextDoseGroup(after: Instant): List<DueDose> {
        val first = nextDose(after) ?: return emptyList()
        return meds.active().filter { it.schedule.type != ScheduleType.AS_NEEDED }.mapNotNull { med ->
            med.schedule.nextOccurrence(after, zone)?.takeIf { it.at == first.at }?.let { DueDose(med, it.at, it.amount) }
        }
    }

    /** Marks doses still unrecorded after the grace period as missed and returns them. */
    suspend fun markMissed(now: Instant, graceMillis: Long): List<DueDose> {
        val cutoff = now.minusMillis(graceMillis)
        val overdue = dueDoses(now, MISSED_LOOKBACK_MILLIS).filter { !it.at.isAfter(cutoff) }
        overdue.forEach { logDose(it.med.id, it.at, DoseStatus.MISSED, it.amount, now) }
        return overdue
    }

    suspend fun adherence(profileId: Long?, from: Instant, to: Instant, now: Instant = Instant.now()): AdherenceStats {
        val (expected, records) = expectedAndRecords(profileId, from, to, now)
        return Adherence.stats(expected, records, now)
    }

    suspend fun streak(profileId: Long?, now: Instant = Instant.now()): Int {
        val today = LocalDate.ofInstant(now, zone)
        val from = today.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(zone).toInstant()
        val (expected, records) = expectedAndRecords(profileId, from, now, now)
        return Adherence.streakDays(expected, records, today, zone, now)
    }

    private suspend fun expectedAndRecords(
        profileId: Long?,
        from: Instant,
        to: Instant,
        now: Instant,
    ): Pair<List<ExpectedDose>, List<DoseRecord>> {
        val medList = meds.all().filter { profileId == null || it.profileId == profileId }
        val ids = medList.map { it.id }.toSet()
        val end = minOf(to, now)
        val expected = medList.filter { it.active && it.schedule.type != ScheduleType.AS_NEEDED }.flatMap { med ->
            val start = maxOf(from, Instant.ofEpochMilli(med.createdAt).minusMillis(DUE_SLACK_MILLIS))
            med.schedule.occurrences(start, end, zone).map { ExpectedDose(med.id, it.at) }
        }
        val records = doses.between(from.toEpochMilli(), end.toEpochMilli() + 1)
            .filter { it.medicationId in ids }
            .map { DoseRecord(it.medicationId, Instant.ofEpochMilli(it.scheduledAt), it.status) }
        return expected to records
    }

    suspend fun history(from: Instant, to: Instant): List<DoseEntity> = doses.between(from.toEpochMilli(), to.toEpochMilli())

    suspend fun allMedications(): List<MedicationEntity> = meds.all()
    suspend fun allDoses(): List<DoseEntity> = doses.all()

    suspend fun markRefillWarned(medId: Long, day: Long) = meds.setLastRefillWarnDay(medId, day)

    /** Replaces everything with the content of a backup, in one transaction. */
    suspend fun replaceAll(
        newProfiles: List<ProfileEntity>,
        newMeds: List<MedicationEntity>,
        newDoses: List<DoseEntity>,
    ) {
        db.withTransaction {
            doses.deleteAll()
            meds.deleteAll()
            profiles.deleteAll()
            newProfiles.forEach { profiles.insert(it) }
            newMeds.forEach { meds.insert(it) }
            newDoses.forEach { doses.upsert(it) }
        }
    }

    companion object {
        const val MAX_NAME = 80
        const val MAX_NOTES = 500
        const val MAX_STOCK = 100_000.0
        private const val DUE_SLACK_MILLIS = 60_000L
        private const val DUE_LOOKBACK_MILLIS = 24 * 60 * 60 * 1000L
        private const val MISSED_LOOKBACK_MILLIS = 3 * 24 * 60 * 60 * 1000L
        private const val STREAK_LOOKBACK_DAYS = 120L
    }
}
