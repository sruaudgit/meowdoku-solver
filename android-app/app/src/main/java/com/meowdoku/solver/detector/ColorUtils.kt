package com.meowdoku.solver.detector

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Port de js/color.js : conversion CIELAB (D65) et Delta E 76.
 */
object ColorUtils {

    data class Lab(val L: Double, val a: Double, val b: Double)

    fun srgb(c: Double): Double {
        val v = if (c < 0) 0.0 else if (c > 255) 255.0 else c
        val s = v / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun f(t: Double): Double =
        if (t > 0.008856) cbrt(t) else (903.3 * t + 16) / 116

    fun rgbToLab(r: Double, g: Double, b: Double): Lab {
        val R = srgb(r)
        val G = srgb(g)
        val B = srgb(b)

        val x = (R * 0.4124564 + G * 0.3575761 + B * 0.1804375) / 0.95047
        val y = (R * 0.2126729 + G * 0.7151522 + B * 0.0721750) / 1.00000
        val z = (R * 0.0193339 + G * 0.1191920 + B * 0.9503041) / 1.08883

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        return Lab(
            L = 116 * fy - 16,
            a = 500 * (fx - fy),
            b = 200 * (fy - fz)
        )
    }

    fun deltaE(lab1: Lab, lab2: Lab): Double {
        val dL = lab1.L - lab2.L
        val da = lab1.a - lab2.a
        val db = lab1.b - lab2.b
        return sqrt(dL * dL + da * da + db * db)
    }

    fun deltaEfromRGB(rgb1: IntArray, rgb2: IntArray): Double =
        deltaE(rgbToLab(rgb1[0].toDouble(), rgb1[1].toDouble(), rgb1[2].toDouble()),
               rgbToLab(rgb2[0].toDouble(), rgb2[1].toDouble(), rgb2[2].toDouble()))

    fun colorsEqual(c1: IntArray, c2: IntArray, threshold: Double = 2.0): Boolean =
        deltaEfromRGB(c1, c2) < threshold

    fun toHex(r: Double, g: Double, b: Double): String {
        val hr = clamp255(r)
        val hg = clamp255(g)
        val hb = clamp255(b)
        return "#" + hexByte(hr) + hexByte(hg) + hexByte(hb)
    }

    fun toHex(rgb: IntArray): String = toHex(rgb[0].toDouble(), rgb[1].toDouble(), rgb[2].toDouble())

    private fun clamp255(v: Double): Int =
        v.toInt().coerceIn(0, 255)

    private fun hexByte(v: Int): String = v.toString(16).padStart(2, '0')
}
