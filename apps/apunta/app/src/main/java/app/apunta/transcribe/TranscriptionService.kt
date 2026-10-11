package app.apunta.transcribe

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.apunta.ApuntaApp
import app.apunta.MainActivity
import app.apunta.R
import app.apunta.app
import app.apunta.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/** Processes the transcription queue one recording at a time, in the foreground so Android keeps it alive. */
class TranscriptionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ConcurrentLinkedQueue<Long>()
    private var worker: Job? = null
    private val cancelled = AtomicLong(-1)
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must follow every startForegroundService call within seconds.
        goForeground("", 0f)
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id >= 0 && !queue.contains(id)) {
                    queue.add(id)
                    scope.launch { app.db.recordings().setStatus(id, Status.QUEUED, 0f) }
                }
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id == _current.value) cancelled.set(id) else queue.remove(id)
                if (id != _current.value) scope.launch { app.db.recordings().setStatus(id, Status.CANCELLED, 0f) }
            }
            ACTION_RESUME_ALL -> scope.launch {
                app.db.recordings().withStatus(listOf(Status.QUEUED, Status.RUNNING)).forEach { if (!queue.contains(it.id)) queue.add(it.id) }
            }
        }
        if (worker?.isActive != true) worker = scope.launch { loop() }
        return START_NOT_STICKY
    }

    private suspend fun loop() {
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "apunta:transcribe").apply { acquire(4 * 3600_000L) }
        try {
            while (true) {
                val id = queue.poll() ?: break
                val rec = app.db.recordings().get(id) ?: continue
                _current.value = id
                cancelled.set(-1)
                var lastNotif = 0L
                val outcome = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    app.transcriber.run(id, { cancelled.get() == id }) { p ->
                        _progress.value = p
                        val now = System.currentTimeMillis()
                        if (now - lastNotif > 1500) { lastNotif = now; goForeground(rec.title, p) }
                    }
                }
                _current.value = null
                _progress.value = 0f
                val dao = app.db.recordings()
                when (outcome) {
                    Transcriber.Outcome.Done -> notifyDone(rec.title, id)
                    Transcriber.Outcome.Cancelled -> dao.setStatus(id, Status.CANCELLED, rec.progress)
                    Transcriber.Outcome.NeedsPro -> dao.setStatus(id, Status.NEEDS_PRO, dao.get(id)?.progress ?: 0f)
                    is Transcriber.Outcome.Failed -> dao.setStatus(id, Status.FAILED, 0f, outcome.reason)
                }
            }
        } finally {
            wake?.let { if (it.isHeld) it.release() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun goForeground(title: String, progress: Float) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, ApuntaApp.CH_WORK)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.transcribing))
            .setContentText(title)
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun notifyDone(title: String, id: Long) {
        val open = PendingIntent.getActivity(
            this, id.toInt(),
            Intent(this, MainActivity::class.java).setAction(ACTION_OPEN).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, ApuntaApp.CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.transcript_ready))
            .setContentText(title).setContentIntent(open).setAutoCancel(true).build()
        try { getSystemService(NotificationManager::class.java).notify(2000 + id.toInt(), n) } catch (_: SecurityException) {}
    }

    override fun onDestroy() {
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_ENQUEUE = "app.apunta.action.ENQUEUE"
        const val ACTION_CANCEL = "app.apunta.action.CANCEL"
        const val ACTION_RESUME_ALL = "app.apunta.action.RESUME_ALL"
        const val ACTION_OPEN = "app.apunta.action.OPEN"
        const val EXTRA_ID = "id"
        private const val NOTIF_ID = 1002

        private val _current = MutableStateFlow<Long?>(null)
        val current: StateFlow<Long?> = _current.asStateFlow()
        private val _progress = MutableStateFlow(0f)
        val progress: StateFlow<Float> = _progress.asStateFlow()

        private fun send(c: Context, action: String, id: Long = -1) = ContextCompat.startForegroundService(
            c, Intent(c, TranscriptionService::class.java).setAction(action).putExtra(EXTRA_ID, id),
        )
        fun enqueue(c: Context, id: Long) = send(c, ACTION_ENQUEUE, id)
        fun cancel(c: Context, id: Long) = send(c, ACTION_CANCEL, id)
        fun resumeAll(c: Context) = send(c, ACTION_RESUME_ALL)
    }
}
