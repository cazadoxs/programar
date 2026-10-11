package io.github.cazadoxs.toma.alarm

import android.content.Context
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.core.Refill
import io.github.cazadoxs.toma.data.DueDose
import java.time.Instant
import java.time.LocalDate

/** The single path every "taken / skipped / undo" goes through, from any screen or button. */
object DoseActions {
    suspend fun record(context: Context, doses: List<DueDose>, status: DoseStatus, now: Instant = Instant.now()) {
        val repo = context.container.repository
        doses.forEach { repo.logDose(it.med.id, it.at, status, it.amount, now) }
        if (status == DoseStatus.TAKEN) checkRefill(context, doses.map { it.med.id }.distinct())
        afterChange(context)
    }

    suspend fun record(context: Context, medId: Long, at: Instant, amount: Double, status: DoseStatus) {
        context.container.repository.logDose(medId, at, status, amount)
        if (status == DoseStatus.TAKEN) checkRefill(context, listOf(medId))
        afterChange(context)
    }

    suspend fun undo(context: Context, medId: Long, at: Instant) {
        context.container.repository.undoDose(medId, at)
        afterChange(context)
    }

    suspend fun takeAsNeeded(context: Context, medId: Long, amount: Double) {
        context.container.repository.logAsNeeded(medId, amount)
        checkRefill(context, listOf(medId))
        afterChange(context)
    }

    suspend fun afterChange(context: Context) {
        val c = context.container
        if (c.repository.dueDoses(Instant.now()).isEmpty()) {
            Notifications.dismissAlarm(context)
            c.scheduler.cancelRering()
        }
        c.scheduler.rescheduleAll()
    }

    private suspend fun checkRefill(context: Context, medIds: List<Long>) {
        val repo = context.container.repository
        val today = LocalDate.now().toEpochDay()
        for (id in medIds) {
            val med = repo.medication(id) ?: continue
            if (med.stock == null || med.lastRefillWarnDay == today) continue
            if (Refill.isLow(med.stock, med.schedule, med.refillWarnDays)) {
                Notifications.refill(context, med, Refill.daysLeft(med.stock, med.schedule))
                repo.markRefillWarned(id, today)
            }
        }
    }
}
