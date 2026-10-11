package io.github.cazadoxs.toma.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val password = "correct horse".toCharArray()
    private val fast = 100_000

    private fun sample() = BackupData(
        createdAtMillis = 1,
        profiles = listOf(BackupProfile(1, "Yo", "🙂", 0)),
        medications = listOf(
            BackupMedication(
                id = 5, profileId = 1, name = "Ibuprofeno", doseLabel = "1 comprimido", form = "PILL",
                colorIndex = 2, notes = "", instructions = "AFTER_FOOD",
                schedule = Schedule(ScheduleType.DAILY, listOf(DoseSlot.of(9, 0)), startEpochDay = 20_000),
                stock = 20.0, createdAtMillis = 1,
            ),
        ),
        doses = listOf(BackupDose(5, 100, DoseStatus.TAKEN, 120, 1.0)),
    )

    @Test
    fun roundTrip() {
        val file = BackupCrypto.encrypt(BackupCodec.encode(sample()), password, fast)
        assertEquals(sample(), BackupCodec.decode(BackupCrypto.decrypt(file, password)))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val file = BackupCrypto.encrypt("hola".toByteArray(), password, fast)
        val e = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(file, "wrong password".toCharArray()) }
        assertEquals(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED, e.reason)
    }

    @Test
    fun tamperingIsDetected() {
        val file = BackupCrypto.encrypt("hola".toByteArray(), password, fast)
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt(file, password) }
    }

    @Test
    fun tamperedHeaderIsDetected() {
        val file = BackupCrypto.encrypt("hola".toByteArray(), password, fast)
        file[10] = (file[10] + 1).toByte() // inside the salt
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt(file, password) }
    }

    @Test
    fun hugeIterationCountInHeaderIsRefused() {
        val file = BackupCrypto.encrypt("hola".toByteArray(), password, fast)
        file[5] = 0x7f
        val e = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(file, password) }
        assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason)
    }

    @Test
    fun randomBytesAreNotABackup() {
        val e = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(ByteArray(100) { it.toByte() }, password) }
        assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason)
    }

    @Test
    fun saltMakesEveryFileDifferent() {
        val a = BackupCrypto.encrypt("hola".toByteArray(), password, fast)
        val b = BackupCrypto.encrypt("hola".toByteArray(), password, fast)
        assertEquals(false, a.contentEquals(b))
        assertArrayEquals("hola".toByteArray(), BackupCrypto.decrypt(b, password))
    }

    @Test
    fun invalidContentIsRejected() {
        val orphan = sample().copy(medications = sample().medications.map { it.copy(profileId = 99) })
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(orphan)) }
        assertThrows(BackupException::class.java) { BackupCodec.decode("{not json".toByteArray()) }
        val badSlot = BackupCodec.encode(sample()).decodeToString().replace("\"minuteOfDay\":540", "\"minuteOfDay\":99999")
        assertThrows(BackupException::class.java) { BackupCodec.decode(badSlot.toByteArray()) }
    }

    @Test
    fun csvNeutralisesFormulas() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, Csv.field("=HYPERLINK(\"x\")"))
        assertEquals("'-5", Csv.field("-5"))
        assertEquals("\"a,b\"", Csv.field("a,b"))
        assertEquals("Paracetamol", Csv.field("Paracetamol"))
    }
}
