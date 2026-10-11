package com.cazadoxs.filtro400.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.Reason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class CallRecord(
    val id: Long,
    val number: String,
    val action: CallAction,
    val reason: Reason,
    val detail: String,
    val timestamp: Long,
    val reported: Boolean,
)

data class Stats(val blocked: Int, val silenced: Int, val allowed: Int) {
    val filtered get() = blocked + silenced
}

/**
 * Historial propio de Filtro 400: solo las llamadas que han pasado por el filtro
 * (las de tus contactos nunca llegan). Así no hace falta el permiso restringido
 * de registro de llamadas. Vive solo en el teléfono y se borra solo.
 */
class HistoryDb(context: Context) :
    SQLiteOpenHelper(context, "history.db", null, 1) {

    /** Se incrementa en cada cambio para que la interfaz se refresque. */
    private val version = MutableStateFlow(0L)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE calls (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL,
                action TEXT NOT NULL,
                reason TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '',
                ts INTEGER NOT NULL,
                reported INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX calls_ts ON calls(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(number: String, action: CallAction, reason: Reason, detail: String, ts: Long) {
        writableDatabase.insert(
            "calls", null,
            ContentValues().apply {
                put("number", number)
                put("action", action.name)
                put("reason", reason.name)
                put("detail", detail)
                put("ts", ts)
            },
        )
        version.value++
    }

    fun recent(limit: Int = 300): Flow<List<CallRecord>> =
        version.map { query(limit) }.flowOn(Dispatchers.IO)

    fun stats(sinceMs: Long): Flow<Stats> = version.map { statsSince(sinceMs) }.flowOn(Dispatchers.IO)

    /** Llamadas filtradas por día (0 = hoy) para la gráfica de la semana. */
    fun dailyFiltered(days: Int, startOfTodayMs: Long): Flow<List<Int>> = version.map {
        val from = startOfTodayMs - (days - 1) * DAY
        val counts = IntArray(days)
        readableDatabase.rawQuery(
            "SELECT ts FROM calls WHERE ts >= ? AND action != ?",
            arrayOf(from.toString(), CallAction.ALLOW.name),
        ).use { c ->
            while (c.moveToNext()) {
                val idx = ((c.getLong(0) - from) / DAY).toInt()
                if (idx in 0 until days) counts[idx]++
            }
        }
        counts.toList()
    }.flowOn(Dispatchers.IO)

    fun countFilteredSince(sinceMs: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM calls WHERE ts >= ? AND action != ?",
            arrayOf(sinceMs.toString(), CallAction.ALLOW.name),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun markReported(id: Long) {
        writableDatabase.update("calls", ContentValues().apply { put("reported", 1) }, "id = ?", arrayOf(id.toString()))
        version.value++
    }

    fun delete(id: Long) {
        writableDatabase.delete("calls", "id = ?", arrayOf(id.toString()))
        version.value++
    }

    fun clear() {
        writableDatabase.delete("calls", null, null)
        version.value++
    }

    fun prune(olderThanMs: Long) {
        writableDatabase.delete("calls", "ts < ?", arrayOf(olderThanMs.toString()))
        version.value++
    }

    private fun query(limit: Int): List<CallRecord> =
        readableDatabase.rawQuery(
            "SELECT id, number, action, reason, detail, ts, reported FROM calls ORDER BY ts DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CallRecord(
                            id = c.getLong(0),
                            number = c.getString(1),
                            action = enumOr(c.getString(2), CallAction.ALLOW),
                            reason = enumOr(c.getString(3), Reason.DEFAULT),
                            detail = c.getString(4),
                            timestamp = c.getLong(5),
                            reported = c.getInt(6) == 1,
                        ),
                    )
                }
            }
        }

    private fun statsSince(sinceMs: Long): Stats {
        var b = 0; var s = 0; var a = 0
        readableDatabase.rawQuery(
            "SELECT action, COUNT(*) FROM calls WHERE ts >= ? GROUP BY action",
            arrayOf(sinceMs.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    CallAction.BLOCK.name -> b = c.getInt(1)
                    CallAction.SILENCE.name -> s = c.getInt(1)
                    else -> a += c.getInt(1)
                }
            }
        }
        return Stats(b, s, a)
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
