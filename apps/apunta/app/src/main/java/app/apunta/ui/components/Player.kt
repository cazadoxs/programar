package app.apunta.ui.components

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

/** Compose-friendly wrapper around MediaPlayer for the detail screen. */
@Stable
class AudioPlayer(private val file: File) {
    private var mp: MediaPlayer? = null
    var playing by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0); private set
    var durationMs by mutableLongStateOf(0); private set
    var speed by mutableFloatStateOf(1f); private set
    var error by mutableStateOf(false); private set

    private fun ensure(): MediaPlayer? {
        mp?.let { return it }
        if (!file.exists()) { error = true; return null }
        return try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                durationMs = duration.toLong()
                setOnCompletionListener { this@AudioPlayer.playing = false; positionMs = durationMs }
                setOnErrorListener { _, _, _ -> error = true; this@AudioPlayer.playing = false; true }
            }.also { mp = it }
        } catch (e: Exception) { error = true; null }
    }

    fun toggle() { if (playing) pause() else play() }

    fun play() {
        val p = ensure() ?: return
        if (positionMs >= durationMs - 200) p.seekTo(0)
        applySpeed(p)
        p.start(); playing = true
    }

    fun pause() { mp?.pause(); playing = false }

    fun seekTo(ms: Long) {
        val p = ensure() ?: return
        p.seekTo(ms.coerceIn(0, durationMs).toInt()); positionMs = ms
    }

    fun playFrom(ms: Long) { seekTo(ms); play() }

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
        mp?.let { if (playing) applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        try { p.playbackParams = PlaybackParams().setSpeed(speed) } catch (_: Exception) {}
    }

    fun tick() { mp?.let { if (playing) positionMs = it.currentPosition.toLong() } }

    fun release() { mp?.release(); mp = null; playing = false }
}

@Composable
fun rememberAudioPlayer(file: File): AudioPlayer {
    val player = remember(file) { AudioPlayer(file) }
    LaunchedEffect(player) { while (true) { player.tick(); delay(200) } }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
