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

        // Détermine la structure de la grille (bordure externe, taille de case,
        // nombre de cases) en ne balayant que les premières lignes (arrêt précoce).
        val pattern = detectPattern(image, equalBg, x0, x1, y0, y1)
        val cellCount = pattern.cellCount
        if (cellCount < 1) {
            throw DetectionException("Impossible de détecter les contours de cases dans la grille.")
        }

        val contourColor = sampleContourColor(
            image, intArrayOf(pattern.borderTop, pattern.borderTop + 5), x0, x1, equalBg
        )

        // Bordures verticales (colonnes) puis horizontales (rangées) des cases.
        // On n'extrapole pas par pas constant : la taille des cases peut varier
        // légèrement (déformation d'écran), ce qui faisait dériver les dernières
        // lignes. Chaque bande est donc détectée à sa position réelle.
        val colBorders = detectBorders(
            image, equalBg, AXIS_VERTICAL, x0, x1, y0, y1, contourColor
        )
        if (colBorders.size != cellCount + 1) {
            throw DetectionException(
                "Grille non carrée détectée (lignes=$cellCount, colonnes=${colBorders.size - 1})."
            )
        }

        // Colonnes de référence (milieux de cases) pour détecter les rangées.
        val sampleXs = IntArray(colBorders.size - 1) { j ->
            Math.floorDiv(colBorders[j][1] + colBorders[j + 1][0], 2)
        }
        val rowBorders = detectBorders(
            image, equalBg, AXIS_HORIZONTAL, x0, x1, y0, y1, contourColor, sampleXs
        )
        if (rowBorders.size != cellCount + 1) {
            throw DetectionException(
                "Grille non carrée détectée (lignes=${rowBorders.size - 1}, colonnes=$cellCount)."
            )
        }
        val size = cellCount

        val cells = mutableListOf<List<Cell>>()
        val colorMap = mutableListOf<ColorEntry>()
        var symbolCount = 0

        for (i in 0 until size) {
            val row = mutableListOf<Cell>()
            val yTop = rowBorders[i][1] + 1
            val yBottom = rowBorders[i + 1][0] - 1
            val cellH = yBottom - yTop + 1

            for (j in 0 until size) {
                val xLeft = colBorders[j][1] + 1
                val xRight = colBorders[j + 1][0] - 1
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

    private const val AXIS_VERTICAL = 0
    private const val AXIS_HORIZONTAL = 1

    private data class Pattern(val borderTop: Int, val cellCount: Int)

    /**
     * Détecte la structure de la grille en faisant un scan vertical à une seule
     * colonne (typiquement le milieu). Le scan horizontal d'origine (isContourLine)
     * échouait sur les grilles fines (ex : 12x12) : sur une rangée contenant une
     * ligne de grille et des cellules colorées, on obtenait plusieurs clusters de
     * couleurs et la ligne n'était pas reconnue comme contour. Le scan vertical ne
     * voit qu'une seule couleur par Y — ligne de grille ou intérieur de cellule.
     */
    private fun detectPattern(
        image: PixelImage,
        equalBg: (IntArray) -> Boolean,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int
    ): Pattern {
        val gap = 8
        val bgColor = image.get(0, 0)

        // Fait un scan vertical à une colonne sX et retourne les bandes de
        // contour fusionnées (stop dès 2 bandes : bordure externe + première
        // bordure interne). Retourne null si aucune couleur de contour trouvée
        // le long de sX (colonne située hors de la grille).
        fun scanContours(sX: Int): MutableList<IntArray>? {
            var contourRef: IntArray? = null
            for (y in y0..y1) {
                val c = image.get(sX, y)
                if (!ColorUtils.colorsEqual(c, bgColor)) {
                    contourRef = c
                    break
                }
            }
            contourRef ?: return null

            val merged = mutableListOf<IntArray>()
            var cur: IntArray? = null
            var curContour = false
            for (y in y0..y1) {
                val isContour = ColorUtils.colorsEqual(image.get(sX, y), contourRef)
                if (cur != null && curContour == isContour) {
                    cur!![1] = y
                } else {
                    if (cur != null && curContour) {
                        val last = merged.lastOrNull()
                        if (last != null && cur!![0] - last[1] <= gap + 1) last[1] = cur!![1]
                        else merged.add(intArrayOf(cur!![0], cur!![1]))
                        // Arrêt précoce : bordure externe puis première bordure interne.
                        if (merged.size >= 2) break
                    }
                    cur = intArrayOf(y, y)
                    curContour = isContour
                }
            }
            if (cur != null && curContour && merged.size < 2) {
                val last = merged.lastOrNull()
                if (last != null && cur!![0] - last[1] <= gap + 1) last[1] = cur!![1]
                else merged.add(intArrayOf(cur!![0], cur!![1]))
            }
            return merged
        }

        var merged = scanContours(Math.floorDiv(x0 + x1, 2))
        if (merged == null || merged.size < 2) {
            // Fallback : si la colonne médiane tombe sur une bordure verticale
            // (toujours contour), on n'obtient qu'une bande. On retente ailleurs.
            val candidates = intArrayOf(
                Math.floorDiv(x0 + x1, 4),
                Math.floorDiv(3 * (x0 + x1), 4)
            )
            for (sX in candidates) {
                merged = scanContours(sX)
                if (merged != null && merged.size >= 2) break
            }
        }
        if (merged == null || merged.size < 2) {
            throw DetectionException("Pattern de grille non détecté.")
        }

        // Première bande de contour = bordure externe, la suivante = bordure interne.
        val borderTop = merged[0][0]
        val outerBorder = merged[0][1] - merged[0][0] + 1
        val innerBorder = merged[1][1] - merged[1][0] + 1
        val cellSize = merged[1][0] - merged[0][1] - 1
        if (cellSize <= 0 || innerBorder < 0) {
            throw DetectionException("Dimensions de grille incohérentes.")
        }

        // Grille carrée : le nombre de cases par côté se déduit de la hauteur totale.
        val totalHeight = y1 - borderTop + 1
        val innerHeight = totalHeight - 2 * outerBorder
        val cellCount = Math.round((innerHeight + innerBorder).toDouble() / (cellSize + innerBorder)).toInt()
        if (cellCount < 1) {
            throw DetectionException("Impossible de compter les cases de la grille.")
        }

        return Pattern(borderTop, cellCount)
    }

    /**
     * Détecte les bandes de contour (bordures de cases) selon un axe.
     * - AXIS_VERTICAL   : vote par colonne x sur des lignes échantillonnées.
     * - AXIS_HORIZONTAL : vote par ligne y sur des colonnes données (sampleXs).
     */
    private fun detectBorders(
        image: PixelImage,
        equalBg: (IntArray) -> Boolean,
        axis: Int,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int,
        contourColor: IntArray,
        sampleXs: IntArray? = null
    ): MutableList<IntArray> {
        val border: (Int, Int) -> Boolean = { px, py -> ColorUtils.colorsEqual(image.get(px, py), contourColor) }

        val vote: IntArray
        if (axis == AXIS_VERTICAL) {
            val samples = mutableListOf<Int>()
            var yy = y0 + 5
            while (yy <= y1 - 5) {
                samples.add(yy)
                yy += 40
            }
            val extent = x1 - x0 + 1
            vote = IntArray(extent)
            for (sy in samples) {
                for (x in x0..x1) if (border(x, sy)) vote[x - x0]++
            }
            val threshold = maxOf(1, samples.size / 2)
            val borders = mutableListOf<IntArray>()
            var cur: IntArray? = null
            for (x in x0..x1) {
                if (vote[x - x0] > threshold) {
                    cur = if (cur != null) intArrayOf(cur[0], x) else intArrayOf(x, x)
                } else {
                    if (cur != null) borders.add(cur)
                    cur = null
                }
            }
            if (cur != null) borders.add(cur)
            return borders
        } else {
            val extent = y1 - y0 + 1
            vote = IntArray(extent)
            for (x in sampleXs!!) {
                for (y in y0..y1) if (border(x, y)) vote[y - y0]++
            }
            val threshold = maxOf(1, sampleXs.size / 2)
            val borders = mutableListOf<IntArray>()
            var cur: IntArray? = null
            for (y in y0..y1) {
                if (vote[y - y0] > threshold) {
                    cur = if (cur != null) intArrayOf(cur[0], y) else intArrayOf(y, y)
                } else {
                    if (cur != null) borders.add(cur)
                    cur = null
                }
            }
            if (cur != null) borders.add(cur)
            return borders
        }
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
