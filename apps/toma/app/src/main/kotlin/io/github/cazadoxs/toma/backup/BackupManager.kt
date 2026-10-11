package io.github.cazadoxs.toma.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.BackupCodec
import io.github.cazadoxs.toma.core.BackupCrypto
import io.github.cazadoxs.toma.core.BackupData
import io.github.cazadoxs.toma.core.BackupDose
import io.github.cazadoxs.toma.core.BackupException
import io.github.cazadoxs.toma.core.BackupMedication
import io.github.cazadoxs.toma.core.BackupProfile
import io.github.cazadoxs.toma.core.Csv
import io.github.cazadoxs.toma.data.DoseEntity
import io.github.cazadoxs.toma.data.Instruction
import io.github.cazadoxs.toma.data.MedForm
import io.github.cazadoxs.toma.data.MedicationEntity
import io.github.cazadoxs.toma.data.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object BackupManager {
    const val MIME = "application/octet-stream"
    const val AUTO_FILE_NAME = "toma-copia-automatica.toma"
    private const val AUTO_WORK = "toma_auto_backup"

    fun suggestedName(): String = "toma-copia-${java.time.LocalDate.now()}.toma"

    suspend fun snapshot(context: Context): BackupData {
        val repo = context.container.repository
        return BackupData(
            createdAtMillis = System.currentTimeMillis(),
            profiles = repo.profiles().map { BackupProfile(it.id, it.name, it.emoji, it.colorIndex) },
            medications = repo.allMedications().map {
                BackupMedication(
                    id = it.id, profileId = it.profileId, name = it.name, doseLabel = it.doseLabel,
                    form = it.form.name, colorIndex = it.colorIndex, notes = it.notes,
                    instructions = it.instruction.name, schedule = it.schedule, stock = it.stock,
                    refillWarnDays = it.refillWarnDays, active = it.active, createdAtMillis = it.createdAt,
                )
            },
            doses = repo.allDoses().map { BackupDose(it.medicationId, it.scheduledAt, it.status, it.loggedAt, it.amount) },
        )
    }

    suspend fun export(context: Context, uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val bytes = BackupCrypto.encrypt(BackupCodec.encode(snapshot(context)), password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw java.io.IOException("cannot open $uri")
    }

    /** Reads, decrypts and validates a backup, then replaces all data with it. */
    suspend fun import(context: Context, uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it) }
            ?: throw java.io.IOException("cannot open $uri")
        val data = BackupCodec.decode(BackupCrypto.decrypt(bytes, password))
        val profiles = data.profiles.map { ProfileEntity(it.id, it.name, it.emoji, it.colorIndex) }
        val medIds = data.medications.map { it.id }.toSet()
        val meds = data.medications.map {
            MedicationEntity(
                id = it.id, profileId = it.profileId, name = it.name, doseLabel = it.doseLabel,
                form = runCatching { MedForm.valueOf(it.form) }.getOrDefault(MedForm.OTHER),
                colorIndex = it.colorIndex, notes = it.notes,
                instruction = runCatching { Instruction.valueOf(it.instructions) }.getOrDefault(Instruction.NONE),
                schedule = it.schedule, stock = it.stock, refillWarnDays = it.refillWarnDays,
                active = it.active, createdAt = it.createdAtMillis,
            )
        }
        val doses = data.doses.filter { it.medicationId in medIds }
            .distinctBy { it.medicationId to it.scheduledAtMillis }
            .map { DoseEntity(0, it.medicationId, it.scheduledAtMillis, it.status, it.loggedAtMillis, it.amount) }
        val c = context.container
        c.repository.replaceAll(profiles, meds, doses)
        c.settings.update { it.copy(activeProfileId = io.github.cazadoxs.toma.settings.Prefs.ALL_PROFILES) }
        c.scheduler.rescheduleAll()
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > BackupCrypto.MAX_FILE_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** History as a spreadsheet-friendly CSV, free for everyone: your data is yours. */
    suspend fun exportCsv(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val repo = context.container.repository
        val profiles = repo.profiles().associateBy { it.id }
        val meds = repo.allMedications().associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val date = DateTimeFormatter.ISO_LOCAL_DATE
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val text = buildString {
            appendLine(Csv.row("fecha", "hora", "perfil", "medicamento", "dosis", "cantidad", "estado", "registrado"))
            for (d in repo.allDoses()) {
                val med = meds[d.medicationId] ?: continue
                val at = Instant.ofEpochMilli(d.scheduledAt).atZone(zone)
                val logged = Instant.ofEpochMilli(d.loggedAt).atZone(zone)
                appendLine(
                    Csv.row(
                        date.format(at), time.format(at), profiles[med.profileId]?.name.orEmpty(), med.name,
                        med.doseLabel, d.amount.toString(), d.status.name,
                        "${date.format(logged)} ${time.format(logged)}",
                    ),
                )
            }
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()) }
            ?: throw java.io.IOException("cannot open $uri")
    }

    fun scheduleAuto(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(AUTO_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork(AUTO_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Writes (or overwrites) the automatic backup file inside the folder the user picked. */
    suspend fun runAuto(context: Context): Boolean = withContext(Dispatchers.IO) {
        val c = context.container
        val prefs = c.settings.current
        if (!prefs.plusOwned || !prefs.autoBackupEnabled || prefs.autoBackupFolder.isBlank()) return@withContext false
        val password = c.secrets.get(SECRET_AUTO_PASSWORD) ?: return@withContext false
        try {
            val tree = Uri.parse(prefs.autoBackupFolder)
            val target = findOrCreate(context, tree) ?: return@withContext false
            export(context, target, password)
            c.settings.update { it.copy(lastAutoBackupAt = System.currentTimeMillis()) }
            true
        } finally {
            password.fill(' ')
        }
    }

    private fun findOrCreate(context: Context, tree: Uri): Uri? {
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == AUTO_FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        return DocumentsContract.createDocument(context.contentResolver, parent, MIME, AUTO_FILE_NAME)
    }

    const val SECRET_AUTO_PASSWORD = "auto_backup_password"
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { BackupManager.runAuto(applicationContext) }.fold({ Result.success() }, { Result.retry() })
}
