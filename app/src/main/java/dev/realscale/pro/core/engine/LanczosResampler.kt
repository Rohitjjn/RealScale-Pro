package dev.realscale.pro.core.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Lanczos-3 resampling on ARGB IntArrays. Offline classical fallback (resize-ncnn `-m lanczos` equivalent). */
object LanczosResampler {
    fun resize(src: IntArray, sw: Int, sh: Int, scale: Int): Pair<IntArray, IntArray> {
        require(scale in 2..4 && src.size == sw * sh)
        val dw = sw * scale; val dh = sh * scale
        val tmp = FloatArray(dw * sh * 4); val dst = IntArray(dw * dh)
        fun px(x: Int, y: Int, c: Int) = (src[y * sw + x] ushr (24 - c * 8)) and 0xFF
        for (y in 0 until sh) for (x in 0 until dw) { // horizontal pass
            val cx = (x + 0.5) / scale - 0.5; var wsum = 0.0; val acc = DoubleArray(4)
            for (t in (cx - 3).toInt()..(cx + 3).toInt()) {
                val w = kernel(cx - t); if (w == 0.0) continue
                val sx = t.coerceIn(0, sw - 1); wsum += w
                for (c in 0..3) acc[c] += w * px(sx, y, c)
            }
            for (c in 0..3) tmp[(y * dw + x) * 4 + c] = (acc[c] / wsum).toFloat()
        }
        for (y in 0 until dh) for (x in 0 until dw) { // vertical pass
            val cy = (y + 0.5) / scale - 0.5; var wsum = 0.0; val acc = DoubleArray(4)
            for (t in (cy - 3).toInt()..(cy + 3).toInt()) {
                val w = kernel(cy - t); if (w == 0.0) continue
                val sy = t.coerceIn(0, sh - 1); wsum += w
                for (c in 0..3) acc[c] += w * tmp[(sy * dw + x) * 4 + c]
            }
            var argb = 0
            for (c in 0..3) argb = argb or (Math.round(acc[c] / wsum).toInt().coerceIn(0, 255) shl (24 - c * 8))
            dst[y * dw + x] = argb
        }
        return dst to intArrayOf(dw, dh)
    }

    internal fun kernel(x: Double): Double = when {
        x == 0.0 -> 1.0; abs(x) >= 3 -> 0.0
        else -> { val px = PI * x; sin(px) / px * (sin(px / 3) / (px / 3)) }
    }
}
