package com.meowdoku.solver.detector

import com.meowdoku.solver.detector.ColorUtils.Lab

/**
 * Port de js/detector.js.
 *
 * Détecte une grille Meowdoku dans une image bitmap. On travaille sur un
 * tableau de pixels RGBA (int[] au format ARGB Android).
 */
object GridDetector {

    class DetectionException(message: String) : Exception(message)

    /**
     * Détecte la grille depuis un bitmap Android (déjà redimensionné à 1080px de large
     * par l'appelant, ou redimensionné ici selon [targetWidth]).
     */
    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        targetWidth: Int = 1080
    ): DetectedGrid {
        val image = if (width != targetWidth) {
            // redimensionne à targetWidth en préservant l'aspect ratio
            resize(pixels, width, height, targetWidth)
        } else {
            PixelImage(width, height, pixels)
        }

        val W = image.width
        val H = image.height
        val equalBg: (IntArray) -> Boolean = { ColorUtils.colorsEqual(it, image.get(0, 0)) }
        val bgColor = image.get(0, 0)

        val hBounds = findHorizontalBounds(image, equalBg, W, H)
        val y0 = hBounds.start
        val y1 = hBounds.end

        val vBounds = findVerticalBounds(image, equalBg, W, y0, y1)
        val x0 = vBounds.start
        val x1 = vBounds.end

        val rawH = findContourBands(image, equalBg, 'h', x0, x1, y0, y1)
        val rawV = findContourBands(image, equalBg, 'v', x0, x1, y0, y1)
        val hc = mergeBands(rawH)
        val vc = mergeBands(rawV)

        val nH = hc.size - 1
        val nV = vc.size - 1
        if (hc.size < 3 || vc.size < 3) {
            throw DetectionException("Impossible de détecter les contours de cases dans la grille.")
        }
        if (nH != nV) {
            throw DetectionException("Grille non carrée détectée (lignes=$nV, colonnes=$nH).")
        }
        val size = nV

        val contourColor = sampleContourColor(image, hc[0], x0, x1, equalBg)

        val cells = mutableListOf<List<Cell>>()
        val colorMap = mutableListOf<ColorEntry>()
        var symbolCount = 0

        for (i in 0 until size) {
            val row = mutableListOf<Cell>()
            val yTop = hc[i][1] + 1
            val yBottom = hc[i + 1][0] - 1
            val cellH = yBottom - yTop + 1

            for (j in 0 until size) {
                val xLeft = vc[j][1] + 1
                val xRight = vc[j + 1][0] - 1
                val cellW = xRight - xLeft + 1

                val inX = minOf(xLeft + 8, xRight)
                val inY = minOf(yTop + 8, yBottom)
                val baseColor = image.get(inX, inY)

                val centerX = xLeft + Math.floorDiv(cellW, 2)
                val centerY = yTop + Math.floorDiv(cellH, 2)
                val centerColor = image.get(centerX, centerY)

                val hasSymbol = !ColorUtils.colorsEqual(baseColor, centerColor)
                val colorIdx = assignColor(colorMap, baseColor)
                val colorHex = ColorUtils.toHex(baseColor)

                row.add(Cell(row = i, col = j, color = colorIdx, hex = colorHex, hasSymbol = hasSymbol))
                if (hasSymbol) symbolCount++
            }
            cells.add(row)
        }

        return DetectedGrid(
            size = size,
            cells = cells,
            backgroundColor = bgColor.copyOf(),
            contourColor = contourColor,
            boundingBox = BoundingBox(x0, y0, Math.abs(x1 - x0) + 1, Math.abs(y1 - y0) + 1),
            colorMap = colorMap.map { ColorUtils.toHex(it.rgb) },
            symbolCount = symbolCount
        )
    }

    // ---- Représentation image ----

    private class PixelImage(val width: Int, val height: Int, val data: IntArray) {
        fun offset(x: Int, y: Int): Int = y * width + x
        fun get(x: Int, y: Int): IntArray {
            if (x < 0 || y < 0 || x >= width || y >= height) return intArrayOf(0, 0, 0)
            val argb = data[offset(x, y)]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return intArrayOf(r, g, b)
        }
    }

    private fun resize(src: IntArray, w: Int, h: Int, targetW: Int): PixelImage {
        val targetH = Math.round(h.toDouble() * (targetW.toDouble() / w.toDouble())).toInt()
        val out = IntArray(targetW * targetH)
        for (y in 0 until targetH) {
            val srcY = (y.toDouble() * h.toDouble() / targetH.toDouble()).toInt().coerceIn(0, h - 1)
            for (x in 0 until targetW) {
                val srcX = (x.toDouble() * w.toDouble() / targetW.toDouble()).toInt().coerceIn(0, w - 1)
                out[y * targetW + x] = src[srcY * w + srcX]
            }
        }
        return PixelImage(targetW, targetH, out)
    }

    // ---- Bornes ----

    private data class Bounds(val start: Int, val end: Int)

    private fun rowInGrid(image: PixelImage, equalBg: (IntArray) -> Boolean, y: Int, W: Int, step: Int): Boolean {
        var nonBg = 0
        var total = 0
        var x = 0
        while (x < W) {
            total++
            if (!equalBg(image.get(x, y))) nonBg++
            x += step
        }
        return nonBg.toDouble() / total > 0.2
    }

    private fun colInGrid(image: PixelImage, equalBg: (IntArray) -> Boolean, x: Int, y0: Int, y1: Int, step: Int): Boolean {
        var nonBg = 0
        var total = 0
        var y = y0
        while (y <= y1) {
            total++
            if (!equalBg(image.get(x, y))) nonBg++
            y += step
        }
        return nonBg.toDouble() / total > 0.2
    }

    private fun longestConsecutiveRun(predicate: (Int) -> Boolean, length: Int): Bounds? {
        var best: Bounds? = null
        var cur: Bounds? = null
        for (i in 0 until length) {
            if (predicate(i)) {
                cur = if (cur != null) Bounds(cur.start, i) else Bounds(i, i)
            } else {
                if (cur != null && (best == null || cur.end - cur.start > best.end - best.start)) best = cur
                cur = null
            }
        }
        if (cur != null && (best == null || cur.end - cur.start > best.end - best.start)) best = cur
        return best
    }

    private fun findHorizontalBounds(image: PixelImage, equalBg: (IntArray) -> Boolean, W: Int, H: Int): Bounds {
        val step = maxOf(1, Math.floorDiv(W, 200))
        val run = longestConsecutiveRun({ i -> rowInGrid(image, equalBg, i, W, step) }, H)
            ?: throw DetectionException("Grille non trouvée dans l'image.")
        return run
    }

    private fun findVerticalBounds(image: PixelImage, equalBg: (IntArray) -> Boolean, W: Int, y0: Int, y1: Int): Bounds {
        val step = maxOf(1, Math.floorDiv(y1 - y0, 200))
        val run = longestConsecutiveRun({ x -> colInGrid(image, equalBg, x, y0 + 5, y1 - 5, step) }, W)
            ?: throw DetectionException("Bornes verticales de la grille non trouvées.")
        return run
    }

    // ---- Contours ----

    private fun isContourLine(
        image: PixelImage,
        equalBg: (IntArray) -> Boolean,
        axis: Char,
        a0: Int,
        a1: Int,
        fixed: Int,
        step: Int
    ): Boolean {
        val colors = mutableListOf<IntArray>()
        var a = a0
        while (a <= a1) {
            val c = if (axis == 'h') image.get(a, fixed) else image.get(fixed, a)
            if (!equalBg(c)) colors.add(c)
            a += step
        }
        if (colors.isEmpty()) return false
        val clusters = mutableListOf<IntArray>()
        for (c in colors) {
            var found = false
            for (cl in clusters) {
                if (ColorUtils.colorsEqual(c, cl)) { found = true; break }
            }
            if (!found) clusters.add(c)
        }
        return clusters.size <= 1
    }

    private fun findContourBands(
        image: PixelImage,
        equalBg: (IntArray) -> Boolean,
        axis: Char,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int
    ): MutableList<IntArray> {
        val bands = mutableListOf<IntArray>()
        var cur: IntArray? = null
        val len = if (axis == 'h') (y1 - y0 + 1) else (x1 - x0 + 1)
        val step = 3
        for (t in 0 until len) {
            val val_: Boolean
            if (axis == 'h') {
                val_ = isContourLine(image, equalBg, 'h', x0, x1, y0 + t, step)
            } else {
                val_ = isContourLine(image, equalBg, 'v', y0, y1, x0 + t, step)
            }
            if (val_) {
                val pos = if (axis == 'h') y0 + t else x0 + t
                cur = if (cur != null) intArrayOf(cur[0], pos) else intArrayOf(pos, pos)
            } else {
                if (cur != null) bands.add(cur)
                cur = null
            }
        }
        if (cur != null) bands.add(cur)
        return bands
    }

    private fun mergeBands(bands: List<IntArray>, gap: Int = 10): List<IntArray> {
        if (bands.isEmpty()) return bands
        val merged = mutableListOf<IntArray>()
        merged.add(bands[0].copyOf())
        for (i in 1 until bands.size) {
            if (bands[i][0] - merged[merged.size - 1][1] < gap) {
                merged[merged.size - 1][1] = bands[i][1]
            } else {
                merged.add(bands[i].copyOf())
            }
        }
        return merged
    }

    private fun sampleContourColor(
        image: PixelImage,
        band: IntArray,
        x0: Int,
        x1: Int,
        equalBg: (IntArray) -> Boolean
    ): IntArray {
        val y = Math.floorDiv(band[0] + band[1], 2)
        val acc = doubleArrayOf(0.0, 0.0, 0.0)
        var count = 0
        for (x in x0..x1) {
            val c = image.get(x, y)
            if (!equalBg(c)) {
                acc[0] += c[0]; acc[1] += c[1]; acc[2] += c[2]
                count++
            }
        }
        if (count == 0) return intArrayOf(255, 255, 255)
        return intArrayOf(
            Math.round(acc[0] / count).toInt(),
            Math.round(acc[1] / count).toInt(),
            Math.round(acc[2] / count).toInt()
        )
    }

    // ---- Couleurs ----

    private data class ColorEntry(val lab: Lab, val rgb: IntArray)

    private fun assignColor(colorMap: MutableList<ColorEntry>, rgb: IntArray): Int {
        val lab = ColorUtils.rgbToLab(rgb[0].toDouble(), rgb[1].toDouble(), rgb[2].toDouble())
        for (i in colorMap.indices) {
            if (ColorUtils.deltaE(lab, colorMap[i].lab) < 3) return i + 1
        }
        colorMap.add(ColorEntry(lab, rgb.copyOf()))
        return colorMap.size
    }
}
