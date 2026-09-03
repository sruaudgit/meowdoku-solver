package com.meowdoku.solver.solver

import com.meowdoku.solver.detector.DetectedGrid
import com.meowdoku.solver.detector.Symbol

/**
 * Port de js/solver.js.
 *
 * Résout une grille Meowdoku : trouve une solution complète (n symboles)
 * par backtracking ligne par ligne.
 *
 * Règles : un seul symbole par ligne, par colonne, par couleur, et aucun
 * symbole adjacent (8-directionnel).
 */
object GridSolver {

    class ConsistencyException(message: String) : Exception(message)

    // Récupère les symboles posés depuis une grille détectée (hasSymbol).
    fun collectSymbols(grid: DetectedGrid): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        for (i in 0 until grid.size) {
            for (j in 0 until grid.size) {
                if (grid.cells[i][j].hasSymbol) {
                    symbols.add(Symbol(i, j, grid.cells[i][j].color))
                }
            }
        }
        return symbols
    }

    // Vérifie que des symboles posés ne se contredisent pas.
    fun checkConsistency(symbols: List<Symbol>, n: Int) {
        for (a in symbols.indices) {
            for (b in a + 1 until symbols.size) {
                val s = symbols[a]
                val t = symbols[b]
                if (s.row == t.row) {
                    throw ConsistencyException("Symboles incohérents : deux symboles sur la ligne ${s.row}.")
                }
                if (s.col == t.col) {
                    throw ConsistencyException("Symboles incohérents : deux symboles sur la colonne ${s.col}.")
                }
                if (s.color == t.color) {
                    throw ConsistencyException("Symboles incohérents : deux symboles de la couleur ${s.color}.")
                }
                if (Math.abs(s.row - t.row) <= 1 && Math.abs(s.col - t.col) <= 1) {
                    throw ConsistencyException(
                        "Symboles incohérents : les symboles (${s.row},${s.col}) et (${t.row},${t.col}) sont adjacents."
                    )
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION")
        n
    }

    /**
     * Résout la grille. Retourne la liste des n symboles ou null si aucune solution.
     */
    fun findSolution(grid: DetectedGrid, fixedSymbols: List<Symbol>): List<Symbol>? {
        val n = grid.size
        checkConsistency(fixedSymbols, n)

        val fixedRow = arrayOfNulls<Symbol>(n)
        val usedCols = mutableSetOf<Int>()
        val usedColors = mutableSetOf<Int>()
        for (s in fixedSymbols) {
            fixedRow[s.row] = s
            usedCols.add(s.col)
            usedColors.add(s.color)
        }

        val placed = mutableListOf<Symbol>()
        val rows = (0 until n).toMutableList()
        rows.sortWith { a, b ->
            when {
                fixedRow[a] != null && fixedRow[b] == null -> -1
                fixedRow[a] == null && fixedRow[b] != null -> 1
                else -> 0
            }
        }

        fun feasible(r: Int, c: Int): Boolean {
            if (usedCols.contains(c)) return false
            if (usedColors.contains(grid.cells[r][c].color)) return false
            for (p in placed) {
                if (Math.abs(p.row - r) <= 1 && Math.abs(p.col - c) <= 1) return false
            }
            return true
        }

        fun backtrack(): Boolean {
            if (placed.size == n) return true
            val r = rows[placed.size]
            val fixed = fixedRow[r]
            if (fixed != null) {
                placed.add(fixed)
                if (backtrack()) return true
                placed.removeAt(placed.size - 1)
                return false
            }
            for (c in 0 until n) {
                if (!feasible(r, c)) continue
                placed.add(Symbol(r, c, grid.cells[r][c].color))
                usedCols.add(c)
                usedColors.add(grid.cells[r][c].color)
                if (backtrack()) return true
                placed.removeAt(placed.size - 1)
                usedCols.remove(c)
                usedColors.remove(grid.cells[r][c].color)
            }
            return false
        }

        val ok = backtrack()
        if (!ok) return null
        return placed.toList()
    }

    private fun colFree(grid: DetectedGrid, r: Int, c: Int): Boolean = true
}
