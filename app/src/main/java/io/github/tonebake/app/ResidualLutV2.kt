package io.github.tonebake.app

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import kotlin.math.roundToInt

@UnstableApi
object ResidualLutV2 {
    private const val LUT_SIZE = 33

    private val luminanceCenters = floatArrayOf(
        0.00000000f, 0.03125000f, 0.06250000f, 0.09375000f, 0.12500000f, 0.15625000f,
        0.18750000f, 0.21875000f, 0.25000000f, 0.28125000f, 0.31250000f, 0.34375000f,
        0.37500000f, 0.40625000f, 0.43750000f, 0.46875000f, 0.50000000f, 0.53125000f,
        0.56250000f, 0.59375000f, 0.62500000f, 0.65625000f, 0.68750000f, 0.71875000f,
        0.75000000f, 0.78125000f, 0.81250000f, 0.84375000f, 0.87500000f, 0.90625000f,
        0.93750000f, 0.96875000f, 1.00000000f
    )

    private val luminanceCurve = floatArrayOf(
        0.00000000f, 0.04082449f, 0.08415129f, 0.13441885f, 0.20404958f, 0.25997514f,
        0.30519560f, 0.33969351f, 0.37092270f, 0.39938224f, 0.43317940f, 0.48874274f,
        0.54264412f, 0.59049686f, 0.63208745f, 0.66115823f, 0.69302038f, 0.75188983f,
        0.76506437f, 0.79664453f, 0.81038943f, 0.85182148f, 0.86440874f, 0.90141743f,
        0.91782589f, 0.93483191f, 0.94045402f, 0.94871605f, 0.96623202f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f
    )

    private val saturationCenters = floatArrayOf(
        0.03125000f, 0.09375000f, 0.15625000f, 0.21875000f,
        0.28125000f, 0.34375000f, 0.40625000f, 0.46875000f,
        0.53125000f, 0.59375000f, 0.65625000f, 0.71875000f,
        0.78125000f, 0.84375000f, 0.90625000f, 0.96875000f
    )

    private val saturationScale = floatArrayOf(
        1.18540961f, 1.18960643f, 1.15848468f, 1.13715049f,
        1.13190794f, 1.13282714f, 1.12984052f, 1.11302084f,
        1.08605156f, 1.09955294f, 1.13619925f, 1.19227254f,
        1.14819913f, 1.16803685f, 1.24782392f, 1.11183377f
    )

    fun create(): SingleColorLut {
        val cube = Array(LUT_SIZE) { Array(LUT_SIZE) { IntArray(LUT_SIZE) } }
        for (rIndex in 0 until LUT_SIZE) {
            for (gIndex in 0 until LUT_SIZE) {
                for (bIndex in 0 until LUT_SIZE) {
                    val r = rIndex.toFloat() / (LUT_SIZE - 1)
                    val g = gIndex.toFloat() / (LUT_SIZE - 1)
                    val b = bIndex.toFloat() / (LUT_SIZE - 1)

                    val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val mappedY = interpolate(y, luminanceCenters, luminanceCurve)
                    val toneScale = if (y > 1e-6f) mappedY / y else 0f
                    val tr = (r * toneScale).coerceIn(0f, 1f)
                    val tg = (g * toneScale).coerceIn(0f, 1f)
                    val tb = (b * toneScale).coerceIn(0f, 1f)

                    val sat = interpolate(mappedY, saturationCenters, saturationScale)
                    val outR = (mappedY + (tr - mappedY) * sat).coerceIn(0f, 1f)
                    val outG = (mappedY + (tg - mappedY) * sat).coerceIn(0f, 1f)
                    val outB = (mappedY + (tb - mappedY) * sat).coerceIn(0f, 1f)

                    cube[rIndex][gIndex][bIndex] = Color.argb(255, q(outR), q(outG), q(outB))
                }
            }
        }
        return SingleColorLut.createFromCube(cube)
    }

    private fun q(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)

    private fun interpolate(x: Float, xs: FloatArray, ys: FloatArray): Float {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        var hi = 1
        while (hi < xs.size && x > xs[hi]) hi++
        val lo = hi - 1
        val t = (x - xs[lo]) / (xs[hi] - xs[lo])
        return ys[lo] + t * (ys[hi] - ys[lo])
    }
}
