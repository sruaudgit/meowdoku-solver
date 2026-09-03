package com.meowdoku.solver.detector

/**
 * Une case de la grille détectée.
 */
data class Cell(
    val row: Int,
    val col: Int,
    val color: Int,          // index de couleur 1..n
    val hex: String,
    var hasSymbol: Boolean = false
)

data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * Grille complète détectée depuis l'image.
 */
data class DetectedGrid(
    val size: Int,
    val cells: List<List<Cell>>,
    val backgroundColor: IntArray,
    val contourColor: IntArray,
    val boundingBox: BoundingBox,
    val colorMap: List<String>,   // hex "#rrggbb" indexées 0..n-1
    val symbolCount: Int
)

/**
 * Un symbole posé / solution.
 */
data class Symbol(
    val row: Int,
    val col: Int,
    val color: Int
)
