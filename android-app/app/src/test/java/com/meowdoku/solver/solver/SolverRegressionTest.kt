package com.meowdoku.solver.solver

import com.meowdoku.solver.detector.Cell
import com.meowdoku.solver.detector.DetectedGrid
import com.meowdoku.solver.detector.BoundingBox
import org.junit.Assert.*
import org.junit.Test

class SolverRegressionTest {

    private fun buildGrid9x9(): DetectedGrid {
        val colors = arrayOf(
            intArrayOf(1,1,1,1,1,1,1,1,1),
            intArrayOf(1,2,2,1,3,1,4,1,4),
            intArrayOf(1,2,1,1,3,3,4,4,4),
            intArrayOf(2,2,2,2,3,4,4,3,4),
            intArrayOf(5,6,2,2,3,3,3,3,4),
            intArrayOf(5,6,6,2,7,3,4,4,4),
            intArrayOf(5,8,6,2,7,7,7,7,4),
            intArrayOf(5,6,6,6,7,9,9,9,9),
            intArrayOf(5,5,6,7,7,7,7,7,7)
        )
        val colorMap = listOf("#8979da","#8bd57c","#fbd982","#f69ce3","#2a8b53","#a86d4b","#39a9c1","#cea400","#d3708f")
        val cells = List(9) { i ->
            List(9) { j ->
                Cell(i, j, colors[i][j], colorMap[colors[i][j] - 1], false)
            }
        }
        return DetectedGrid(
            size = 9,
            cells = cells,
            colorMap = colorMap,
            backgroundColor = intArrayOf(0xf7, 0xf1, 0xef),
            contourColor = intArrayOf(0xff, 0xff, 0xff),
            boundingBox = BoundingBox(0, 0, 100, 100),
            symbolCount = 0
        )
    }

    @Test
    fun solver9x9_matchesExpectedSolution() {
        val grid = buildGrid9x9()
        val fixedSymbols = GridSolver.collectSymbols(grid)
        println("Symboles fixed: ${fixedSymbols.size}")

        val solution = GridSolver.findSolution(grid, fixedSymbols)
        assertNotNull("Le solver devrait trouver une solution", solution)

        println("=== Solution Kotlin ===")
        for (s in solution!!.sortedBy { it.row }) {
            println("  row=${s.row} col=${s.col} color=${s.color}")
        }

        // Vérifie les contraintes
        val colors = solution.map { it.color }.toSet()
        assertEquals("9 couleurs différentes", 9, colors.size)
        assertEquals("couleurs 1..9", (1..9).toSet(), colors)

        val cols = solution.map { it.col }.toSet()
        assertEquals("9 colonnes différentes", 9, cols.size)

        // Vérifie la solution attendue
        val expected = listOf(
            intArrayOf(6, 1),  // row=0
            intArrayOf(8, 4),  // row=1
            intArrayOf(5, 3),  // row=2
            intArrayOf(3, 2),  // row=3
            intArrayOf(0, 5),  // row=4
            intArrayOf(4, 7),  // row=5
            intArrayOf(1, 8),  // row=6
            intArrayOf(7, 9),  // row=7
            intArrayOf(2, 6)   // row=8
        )

        for (s in solution) {
            val exp = expected[s.row]
            assertEquals("row ${s.row}: col", exp[0], s.col)
            assertEquals("row ${s.row}: color", exp[1], s.color)
        }
    }
}
