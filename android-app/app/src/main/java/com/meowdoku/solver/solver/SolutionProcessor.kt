package com.meowdoku.solver.solver

import android.content.Context
import android.graphics.Bitmap
import com.meowdoku.solver.detector.GridDetector
import com.meowdoku.solver.ui.GridRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * Pipeline commun de résolution : redimensionne l'image source au format
 * attendu par le détecteur, détecte la grille, résout et rend l'image de
 * solution dans le cache.
 *
 * Utilisé par [com.meowdoku.solver.service.SolveService] (partage) et
 * [com.meowdoku.solver.service.CaptureService] (capture d'écran).
 */
object SolutionProcessor {

    const val TARGET_WIDTH = 1080

    /**
     * Résout [sourceBitmap] et retourne le chemin absolu du PNG de solution.
     * Lève [GridDetector.DetectionException] ou [GridSolver.ConsistencyException].
     */
    fun solveAndSave(context: Context, sourceBitmap: Bitmap): String {
        val scaled = if (sourceBitmap.width == TARGET_WIDTH) {
            sourceBitmap
        } else {
            val scale = TARGET_WIDTH.toFloat() / sourceBitmap.width.toFloat()
            val targetHeight = (sourceBitmap.height.toFloat() * scale).toInt().coerceAtLeast(1)
            val copy = Bitmap.createScaledBitmap(sourceBitmap, TARGET_WIDTH, targetHeight, false)
            sourceBitmap.recycle()
            copy
        }

        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        scaled.recycle()

        val grid = GridDetector.detect(pixels, width, height)
        val fixedSymbols = GridSolver.collectSymbols(grid)
        val solution = GridSolver.findSolution(grid, fixedSymbols)
            ?: throw GridSolver.ConsistencyException("Aucune solution trouvée avec les symboles posés.")

        val solutionBmp = GridRenderer.renderSolution(grid, solution)

        val solutionsDir = File(context.cacheDir, "solutions").apply { mkdirs() }
        val solutionFile = File(solutionsDir, "solution_${System.currentTimeMillis()}.png")
        FileOutputStream(solutionFile).use { out ->
            solutionBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        solutionBmp.recycle()

        return solutionFile.absolutePath
    }
}