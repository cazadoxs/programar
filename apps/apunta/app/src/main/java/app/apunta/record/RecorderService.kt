package app.apunta.record

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.apunta.ApuntaApp
import app.apunta.MainActivity
import app.apunta.R
import app.apunta.app
import app.apunta.audio.ChunkPlanner
import app.apunta.data.Recording
import app.apunta.data.Source
import app.apunta.data.Status
import app.apunta.transcribe.TranscriptionService
import app.apunta.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.DateFormat
import java.util.Date

data class RecorderUiState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val levels: List<Float> = emptyList(),
    val bookmarks: List<Long> = emptyList(),
    val lastSavedId: Long? = null,
    val error: String? = null,
)

/**
 * Foreground service (type microphone) that records WAV 16 kHz mono 16-bit. WAV is
 * written straight to disk with a header patched at the end, so if the phone dies
 * mid-class everything up to the last second is still a valid, recoverable file.
 */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    @Volatile private var paused = false
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> { paused = !paused; _state.value = _state.value.copy(paused = paused); updateNotification() }
            ACTION_BOOKMARK -> bookmark()
            ACTION_STOP -> stopRequested = true
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (job?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = RecorderUiState(error = "permission"); stopSelf(); return
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            _state.value = RecorderUiState(error = "foreground"); stopSelf(); return
        }
        paused = false; stopRequested = false
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "apunta:rec").apply { acquire(6 * 3600_000L) }
        _state.value = RecorderUiState(active = true)
        job = scope.launch { record() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun record() {
        val rate = ChunkPlanner.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, rate * 2)
        val source = if (app.settings.current.highQualityAudio) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val rec = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        } catch (e: Exception) { null }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release(); finish(null, "mic"); return
        }
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val name = "rec_${System.currentTimeMillis()}.wav"
        val file = File(dir, name)
        val raf = RandomAccessFile(file, "rw")
        raf.write(ByteArray(44))
        val buf = ShortArray(rate / 10)
        val bytes = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        var samples = 0L
        var lastHeader = 0L
        val levels = ArrayList<Float>()
        var micError = false
        try {
            rec.startRecording()
            while (!stopRequested) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) { micError = true; break }
                if (n == 0) continue
                if (paused) { continue }
                bytes.clear()
                for (i in 0 until n) bytes.putShort(buf[i])
                raf.write(bytes.array(), 0, n * 2)
                samples += n
                val lvl = ChunkPlanner.rms(buf, n)
                levels += lvl
                if (levels.size > 60) levels.removeAt(0)
                _state.value = _state.value.copy(
                    elapsedMs = samples * 1000 / rate, level = lvl, levels = levels.toList(),
                )
                if (samples - lastHeader > rate * 5L) { writeWavHeader(raf, samples, rate); lastHeader = samples }
                if ((samples * 2) > MAX_BYTES) break  // ~4 h, keeps WAV < 2 GB
                if (samples % (rate * 30L) < n) updateNotification()
            }
        } catch (e: java.io.IOException) {
            // disk full: keep what we have
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            writeWavHeader(raf, samples, rate)
            raf.close()
        }
        if (samples > rate) {
            finish(Triple(file, name, samples), if (micError) "mic" else null)
        } else {
            file.delete()
            finish(null, if (micError) "mic" else "short")
        }
    }

    private fun finish(result: Triple<File, String, Long>?, error: String?) {
        var id: Long? = null
        if (result != null) {
            val (_, name, samples) = result
            val s = app.settings.current
            val title = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
            val rec = Recording(
                title = title, createdAt = System.currentTimeMillis(),
                durationMs = samples * 1000 / ChunkPlanner.SAMPLE_RATE, fileName = name, source = Source.RECORDED,
                language = s.language, modelId = null, status = Status.NEW,
                bookmarks = _state.value.bookmarks.joinToString(","),
            )
            id = kotlinx.coroutines.runBlocking { app.db.recordings().insert(rec) }
            if (s.autoTranscribe) TranscriptionService.enqueue(this, id!!)
        }
        _state.value = RecorderUiState(lastSavedId = id, error = error)
        wake?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun bookmark() {
        val st = _state.value
        if (!st.active) return
        _state.value = st.copy(bookmarks = st.bookmarks + st.elapsedMs)
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, label: Int, code: Int) = NotificationCompat.Action.Builder(
            0, getString(label),
            PendingIntent.getService(this, code, Intent(this, RecorderService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return NotificationCompat.Builder(this, ApuntaApp.CH_RECORD)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(if (paused) R.string.rec_paused else R.string.rec_recording))
            .setContentText(TimeFormat.clock(elapsedMs))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action(ACTION_PAUSE, if (paused) R.string.rec_resume else R.string.rec_pause, 1))
            .addAction(action(ACTION_STOP, R.string.rec_stop, 2))
            .build()
    }

    private fun updateNotification() {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(_state.value.elapsedMs))
    }

    override fun onDestroy() {
        stopRequested = true
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.apunta.action.REC_START"
        const val ACTION_STOP = "app.apunta.action.REC_STOP"
        const val ACTION_PAUSE = "app.apunta.action.REC_PAUSE"
        const val ACTION_BOOKMARK = "app.apunta.action.REC_BOOKMARK"
        private const val NOTIF_ID = 1001
        private const val MAX_BYTES = 1_900_000_000L

        private val _state = MutableStateFlow(RecorderUiState())
        val state: StateFlow<RecorderUiState> = _state.asStateFlow()

        fun clearResult() { _state.value = _state.value.copy(lastSavedId = null, error = null) }

        fun send(context: Context, action: String) {
            val i = Intent(context, RecorderService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, i) else context.startService(i)
        }

        fun writeWavHeader(raf: RandomAccessFile, samples: Long, rate: Int) {
            val dataLen = samples * 2
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt((36 + dataLen).toInt()).put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            h.put("data".toByteArray()).putInt(dataLen.toInt())
            val pos = raf.filePointer
            raf.seek(0); raf.write(h.array()); raf.seek(pos)
        }
    }
}
