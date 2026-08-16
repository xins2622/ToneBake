package io.github.tonebake.app

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import kotlin.math.roundToInt

@UnstableApi
object CalibratedLut {
    private const val LUT_SIZE = 33
    private val luminanceCenters = floatArrayOf(0.00781250f, 0.02343750f, 0.03906250f, 0.05468750f, 0.07031250f, 0.08593750f, 0.10156250f, 0.11718750f, 0.13281250f, 0.14843750f, 0.16406250f, 0.17968750f, 0.19531250f, 0.21093750f, 0.22656250f, 0.24218750f, 0.25781250f, 0.27343750f, 0.28906250f, 0.30468750f, 0.32031250f, 0.33593750f, 0.35156250f, 0.36718750f, 0.38281250f, 0.39843750f, 0.41406250f, 0.42968750f, 0.44531250f, 0.46093750f, 0.47656250f, 0.49218750f, 0.50781250f, 0.52343750f, 0.53906250f, 0.55468750f, 0.57031250f, 0.58593750f, 0.60156250f, 0.61718750f, 0.63281250f, 0.64843750f, 0.66406250f, 0.67968750f, 0.69531250f, 0.71093750f, 0.72656250f, 0.74218750f, 0.75781250f, 0.77343750f, 0.78906250f, 0.80468750f, 0.82031250f, 0.83593750f, 0.85156250f, 0.86718750f, 0.88281250f, 0.89843750f, 0.91406250f, 0.92968750f, 0.94531250f, 0.96093750f, 0.97656250f, 0.99218750f)
    private val luminanceCurve = floatArrayOf(0.00780879f, 0.04887736f, 0.08754453f, 0.11610370f, 0.15022163f, 0.18633854f, 0.22137118f, 0.25584575f, 0.28909343f, 0.31804351f, 0.34756459f, 0.37281086f, 0.39511188f, 0.41315902f, 0.43247025f, 0.45800574f, 0.48953649f, 0.51129237f, 0.53740013f, 0.56475795f, 0.58547026f, 0.60924974f, 0.62746536f, 0.64782933f, 0.66501751f, 0.68324228f, 0.70436541f, 0.72212205f, 0.74553855f, 0.76064797f, 0.76713892f, 0.78058138f, 0.79919871f, 0.80436522f, 0.82110636f, 0.84131080f, 0.84399476f, 0.85758250f, 0.86738067f, 0.87066239f, 0.88267993f, 0.89614450f, 0.90208996f, 0.91578456f, 0.92409716f, 0.92687217f, 0.93542633f, 0.93558660f, 0.94184639f, 0.94590378f, 0.95455981f, 0.95998329f, 0.97052995f, 0.97749638f, 0.98561866f, 0.98935846f, 0.98935846f, 0.99371208f, 0.99371208f, 0.99371208f, 0.99371208f, 0.99371208f, 0.99371208f, 0.99371208f)
    private val saturationCenters = floatArrayOf(0.03125000f, 0.09375000f, 0.15625000f, 0.21875000f, 0.28125000f, 0.34375000f, 0.40625000f, 0.46875000f, 0.53125000f, 0.59375000f, 0.65625000f, 0.71875000f, 0.78125000f, 0.84375000f, 0.90625000f, 0.96875000f)
    private val saturationScale = floatArrayOf(0.73735032f, 1.00599786f, 0.96680253f, 0.91225161f, 0.86519176f, 0.89412055f, 0.91620256f, 0.92942938f, 0.93096456f, 0.94051047f, 0.97231523f, 0.99802159f, 1.00329049f, 1.04136141f, 1.13129531f, 1.21409869f)

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
