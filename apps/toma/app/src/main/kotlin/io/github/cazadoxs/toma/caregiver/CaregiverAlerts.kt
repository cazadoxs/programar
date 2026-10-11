package io.github.cazadoxs.toma.caregiver

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.alarm.Notifications
import io.github.cazadoxs.toma.data.DueDose
import io.github.cazadoxs.toma.data.Repository
import io.github.cazadoxs.toma.settings.Prefs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Caregiver mode: when a dose stays unconfirmed, a push message goes to the person who looks
 * after the patient through ntfy (open source, free app, no account). Off by default; the
 * message carries only the profile name and the time unless the user chooses to add names.
 */
object CaregiverAlerts {
    private val TOPIC_REGEX = Regex("^[A-Za-z0-9_-]{12,64}$")

    fun newTopic(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return "toma-" + (1..20).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    fun isValidTopic(topic: String) = TOPIC_REGEX.matches(topic)

    /** Only https servers with a plain host are accepted, so the message can't be redirected to cleartext. */
    fun normalizeServer(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        if (!uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return null
        return trimmed
    }

    fun subscribeUrl(prefs: Prefs) = "${prefs.caregiverServer}/${prefs.caregiverTopic}"

    suspend fun onMissed(context: Context, missed: List<DueDose>, repository: Repository, prefs: Prefs) {
        if (!prefs.caregiverEnabled || !isValidTopic(prefs.caregiverTopic)) return
        val profiles = repository.profiles().associateBy { it.id }
        missed.groupBy { it.med.profileId }.forEach { (profileId, doses) ->
            val who = profiles[profileId]?.name ?: ""
            val times = doses.map { Notifications.timeText(it.at) }.distinct().joinToString(", ")
            val title = context.getString(R.string.caregiver_push_title, who)
            val body = if (prefs.caregiverIncludeMeds) {
                context.getString(R.string.caregiver_push_body_meds, times, doses.joinToString(", ") { it.med.name })
            } else {
                context.getString(R.string.caregiver_push_body, times)
            }
            enqueue(context, prefs, title, body)
        }
    }

    fun sendTest(context: Context, prefs: Prefs) {
        enqueue(context, prefs, context.getString(R.string.caregiver_test_title), context.getString(R.string.caregiver_test_body))
    }

    private fun enqueue(context: Context, prefs: Prefs, title: String, body: String) {
        val request = OneTimeWorkRequestBuilder<CaregiverWorker>()
            .setInputData(
                workDataOf(
                    CaregiverWorker.KEY_SERVER to prefs.caregiverServer,
                    CaregiverWorker.KEY_TOPIC to prefs.caregiverTopic,
                    CaregiverWorker.KEY_TITLE to title,
                    CaregiverWorker.KEY_BODY to body,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

class CaregiverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val server = CaregiverAlerts.normalizeServer(inputData.getString(KEY_SERVER).orEmpty()) ?: return Result.failure()
        val topic = inputData.getString(KEY_TOPIC).orEmpty()
        if (!CaregiverAlerts.isValidTopic(topic)) return Result.failure()
        val payload = buildJsonObject {
            put("topic", topic)
            put("title", inputData.getString(KEY_TITLE).orEmpty().take(200))
            put("message", inputData.getString(KEY_BODY).orEmpty().take(1000))
            put("priority", 5)
            putJsonArray("tags") { add(kotlinx.serialization.json.JsonPrimitive("pill")) }
        }.toString().toByteArray()
        return try {
            val connection = URI("$server/").toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(payload) }
                when (connection.responseCode) {
                    in 200..299 -> Result.success()
                    429, in 500..599 -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                    else -> Result.failure()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SERVER = "server"
        const val KEY_TOPIC = "topic"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        private const val MAX_ATTEMPTS = 6
    }
}
