package app.apunta.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes any audio or video file Android understands (m4a, mp3, ogg/opus from
 * WhatsApp, wav, flac, mp4, webm...) into raw 16 kHz mono signed 16-bit PCM,
 * which is what whisper wants. The output is written to disk so hours of audio
 * never sit in memory.
 */
object AudioDecoder {

    class DecodeException(message: String, cause: Throwable? = null) : IOException(message, cause)

    data class Result(val samples: Long) {
        val durationMs: Long get() = samples * 1000 / ChunkPlanner.SAMPLE_RATE
    }

    fun decodeToPcm16k(
        input: File,
        output: File,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            throw DecodeException("Unsupported file", e)
        }
        var codec: MediaCodec? = null
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw DecodeException("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var resampler = Resampler(sampleRate, ChunkPlanner.SAMPLE_RATE)
            var written = 0L

            BufferedOutputStream(FileOutputStream(output), 1 shl 16).use { out ->
                val writeBuf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
                fun write(samples: FloatArray) {
                    for (s in samples) {
                        if (writeBuf.remaining() < 2) {
                            out.write(writeBuf.array(), 0, writeBuf.position()); writeBuf.clear()
                        }
                        val v = (s * 32767f).coerceIn(-32768f, 32767f)
                        writeBuf.putShort(v.toInt().toShort())
                    }
                    written += samples.size
                }

                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var mono = FloatArray(0)
                var lastProgress = -1
                while (!outputDone) {
                    if (isCancelled()) throw InterruptedException("cancelled")
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buf = codec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val newRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                            if (newRate != sampleRate) {
                                write(resampler.flush())
                                sampleRate = newRate
                                resampler = Resampler(sampleRate, ChunkPlanner.SAMPLE_RATE)
                            }
                        }
                        outIndex >= 0 -> {
                            val buf = codec.getOutputBuffer(outIndex)!!
                            if (info.size > 0) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                val bytesPerSample = if (floatPcm) 4 else 2
                                val frames = info.size / bytesPerSample / channels.coerceAtLeast(1)
                                if (mono.size < frames) mono = FloatArray(frames)
                                val ordered = buf.order(ByteOrder.nativeOrder())
                                for (fIdx in 0 until frames) {
                                    var acc = 0f
                                    for (c in 0 until channels) {
                                        acc += if (floatPcm) ordered.float else ordered.short / 32768f
                                    }
                                    mono[fIdx] = acc / channels
                                }
                                write(resampler.process(mono, frames))
                                if (durationUs > 0) {
                                    val p = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                                    if (p != lastProgress) { lastProgress = p; onProgress(p / 100f) }
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
                write(resampler.flush())
                if (writeBuf.position() > 0) out.write(writeBuf.array(), 0, writeBuf.position())
            }
            if (written == 0L) throw DecodeException("Empty audio")
            return Result(written)
        } catch (e: DecodeException) {
            output.delete(); throw e
        } catch (e: InterruptedException) {
            output.delete(); throw e
        } catch (e: Exception) {
            output.delete(); throw DecodeException("Could not decode audio", e)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor.release()
        }
    }

    /** Reads [count] samples starting at [startSample] from a PCM file as floats in -1..1. */
    fun readFloats(pcm: File, startSample: Long, count: Int): FloatArray {
        val out = FloatArray(count)
        java.io.RandomAccessFile(pcm, "r").use { raf ->
            raf.seek(startSample * 2)
            val bytes = ByteArray(count * 2)
            var read = 0
            while (read < bytes.size) {
                val r = raf.read(bytes, read, bytes.size - read)
                if (r < 0) break
                read += r
            }
            val bb = ByteBuffer.wrap(bytes, 0, read).order(ByteOrder.LITTLE_ENDIAN)
            val n = read / 2
            for (i in 0 until n) out[i] = bb.short / 32768f
            return if (n == count) out else out.copyOf(n)
        }
    }

    fun sampleCount(pcm: File): Long = pcm.length() / 2
}
