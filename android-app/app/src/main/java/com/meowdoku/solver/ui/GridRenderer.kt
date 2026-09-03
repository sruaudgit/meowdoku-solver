package com.meowdoku.solver.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.meowdoku.solver.detector.ColorUtils
import com.meowdoku.solver.detector.DetectedGrid
import com.meowdoku.solver.detector.Symbol

/**
 * Dessine une grille Meowdoku résolue sur un Bitmap.
 */
object GridRenderer {

    /** Le rendu est carré : [size] pixels, la grille occupe toute la surface. */
    fun renderSolution(grid: DetectedGrid, solution: List<Symbol>, pixelSize: Int = 900): Bitmap {
        val bmp = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val n = grid.size

        // Fond blanc
        canvas.drawColor(Color.WHITE)

        val cellSize = pixelSize.toFloat() / n.toFloat()

        // Grille de case et padding externe
        val inset = cellSize * 0.08f

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Couleur des cases depuis colorMap (1-indexé : index = color - 1)
        for (i in 0 until n) {
            for (j in 0 until n) {
                val colorIdx = grid.cells[i][j].color
                val hex = if (colorIdx - 1 in grid.colorMap.indices) {
                    grid.colorMap[colorIdx - 1]
                } else {
                    "#CCCCCC"
                }
                val rgb = parseHex(hex)
                cellPaint.color = Color.rgb(rgb[0], rgb[1], rgb[2])

                val left = j * cellSize + inset
                val top = i * cellSize + inset
                val right = (j + 1) * cellSize - inset
                val bottom = (i + 1) * cellSize - inset
                canvas.drawRoundRect(left, top, right, bottom, inset * 0.5f, inset * 0.5f, cellPaint)
            }
        }

        // Dessine les symboles de solution
        val starSize = cellSize * 0.45f
        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = starSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            textAlign = Paint.Align.CENTER
            textSize = starSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        for (s in solution) {
            val cx = (s.col + 0.5f) * cellSize
            val cy = (s.row + 0.5f) * cellSize
            val base = starSize * 0.62f
            canvas.drawText("\u2605", cx, cy + base, shadowPaint)
            canvas.drawText("\u2605", cx, cy + base - starSize * 0.05f, symbolPaint)
        }

        return bmp
    }

    private fun parseHex(hex: String): IntArray {
        val h = hex.removePrefix("#")
        if (h.length == 6) {
            return intArrayOf(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16)
            )
        }
        return intArrayOf(204, 204, 204)
    }
}
