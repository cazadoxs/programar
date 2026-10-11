package vlc.transit.core.util

import java.io.ByteArrayOutputStream

class VarintWriter(initial: Int = 256) {
    private val out = ByteArrayOutputStream(initial)
    fun write(value: Int) {
        require(value >= 0) { "varint must be non-negative: $value" }
        var v = value
        while (v and 0x7f.inv() != 0) {
            out.write((v and 0x7f) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }
    fun byte(b: Int) = out.write(b)
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Bounds-checked reader: a corrupt blob throws instead of reading past the end. */
class VarintReader(private val buf: ByteArray, private var pos: Int = 0) {
    val hasMore get() = pos < buf.size
    fun byte(): Int {
        if (pos >= buf.size) throw IllegalStateException("truncated blob")
        return buf[pos++].toInt() and 0xff
    }
    fun read(): Int {
        var shift = 0
        var result = 0
        while (true) {
            if (shift > 28) throw IllegalStateException("varint too long")
            val b = byte()
            result = result or ((b and 0x7f) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
