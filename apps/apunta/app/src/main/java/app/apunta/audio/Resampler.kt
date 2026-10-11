package app.apunta.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Streaming mono resampler to 16 kHz (or any rate) for speech.
 *
 * When downsampling it first runs a windowed-sinc low-pass FIR so that content
 * above the new Nyquist frequency does not fold back as noise, then
 * interpolates linearly. That is plenty for speech recognition and cheap enough
 * to run over hours of audio on a phone.
 *
 * Feed blocks with [process] as they come out of the decoder; call [flush] at the end.
 */
class Resampler(private val inRate: Int, private val outRate: Int) {
    init {
        require(inRate > 0 && outRate > 0) { "Sample rates must be positive" }
    }

    private val step = inRate.toDouble() / outRate
    private val taps: FloatArray = if (inRate > outRate) lowPass(0.5 * outRate / inRate * 0.9, 63) else floatArrayOf(1f)
    private val history = FloatArray(taps.size - 1)
    private var position = 0.0          // fractional read position in the filtered stream
    private var carry = Float.NaN       // last filtered sample of the previous block

    fun process(input: FloatArray, length: Int = input.size): FloatArray {
        if (length == 0) return FloatArray(0)
        val f = filter(input, length)
        // Prepend the previous block's last sample so interpolation is seamless.
        val src: FloatArray
        val offset: Int
        if (carry.isNaN()) {
            src = f; offset = 0
        } else {
            src = FloatArray(f.size + 1)
            src[0] = carry
            System.arraycopy(f, 0, src, 1, f.size)
            offset = 1
        }
        // position is relative to the first sample of this block (index `offset` in src)
        var p = position + offset
        val capacity = ((src.size - p) / step).toInt() + 2
        val out = FloatArray(capacity.coerceAtLeast(0))
        var n = 0
        while (p + 1 < src.size && n < out.size) {
            val i = floor(p).toInt()
            val frac = (p - i).toFloat()
            out[n++] = src[i] + (src[i + 1] - src[i]) * frac
            p += step
        }
        position = p - offset - f.size
        carry = f[f.size - 1]
        return if (n == out.size) out else out.copyOf(n)
    }

    /** Emits the final sample(s) held back for interpolation. */
    fun flush(): FloatArray {
        if (carry.isNaN()) return FloatArray(0)
        val out = ArrayList<Float>()
        var p = position
        while (p <= 0.0) { out.add(carry); p += step }
        carry = Float.NaN
        return out.toFloatArray()
    }

    private fun filter(input: FloatArray, length: Int): FloatArray {
        if (taps.size == 1) return input.copyOf(length)
        val n = taps.size
        val buf = FloatArray(history.size + length)
        System.arraycopy(history, 0, buf, 0, history.size)
        System.arraycopy(input, 0, buf, history.size, length)
        val filtered = FloatArray(length)
        for (i in 0 until length) {
            var acc = 0f
            for (k in 0 until n) acc += buf[i + k] * taps[k]
            filtered[i] = acc
        }
        System.arraycopy(buf, length, history, 0, history.size)
        return filtered
    }

    companion object {
        /** Windowed-sinc (Blackman) low-pass; cutoff is a fraction of the input rate. */
        fun lowPass(cutoff: Double, size: Int): FloatArray {
            val m = size - 1
            val h = DoubleArray(size) { i ->
                val x = i - m / 2.0
                val sinc = if (x == 0.0) 2 * PI * cutoff else sin(2 * PI * cutoff * x) / x
                val w = 0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m)
                sinc * w
            }
            val sum = h.sum()
            return FloatArray(size) { (h[it] / sum).toFloat() }
        }
    }
}
