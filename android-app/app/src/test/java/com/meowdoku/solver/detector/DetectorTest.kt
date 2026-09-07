package com.meowdoku.solver.detector

import org.junit.Test
import java.io.File

class DetectorTest {

    @Test
    fun detectSampleGrid() {
        val resource = File("src/test/resources/sample.rgba")
        if (!resource.exists()) {
            println("SKIP: sample.rgba non trouvé")
            return
        }
        val raw = resource.readBytes()
        val W = 1080
        val H = 2340
        val pixels = IntArray(W * H)
        var i = 0
        var j = 0
        while (i + 2 < raw.size && j < pixels.size) {
            val r = raw[i].toInt() and 0xFF
            val g = raw[i + 1].toInt() and 0xFF
            val b = raw[i + 2].toInt() and 0xFF
            pixels[j] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            i += 3
            j++
        }

        println("Pixels lus : $j / ${W * H}")
        val start = System.nanoTime()
        val grid = GridDetector.detect(pixels, W, H)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Détection en : ${ms}ms")
        println("Taille : ${grid.size}x${grid.size}")
        println("Symboles : ${grid.symbolCount}")
        println("colorMap : ${grid.colorMap}")
        for (r in 0 until grid.size) {
            val row = grid.cells[r].joinToString(" ") { c ->
                (if (c.hasSymbol) "*" else ".") + c.color.toString().padStart(2)
            }
            println(row)
        }
    }

    @Test
    fun detectSampleGrid12x12() {
        val resource = File("src/test/resources/sample_12x12.rgba")
        if (!resource.exists()) {
            println("SKIP: sample_12x12.rgba non trouvé")
            return
        }
        val raw = resource.readBytes()
        val W = 1080
        val H = 2340
        val pixels = IntArray(W * H)
        var i = 0
        var j = 0
        while (i + 2 < raw.size && j < pixels.size) {
            val r = raw[i].toInt() and 0xFF
            val g = raw[i + 1].toInt() and 0xFF
            val b = raw[i + 2].toInt() and 0xFF
            pixels[j] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            i += 3
            j++
        }

        val start = System.nanoTime()
        val grid = GridDetector.detect(pixels, W, H)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Détection 12x12 en : ${ms}ms")
        println("Taille : ${grid.size}x${grid.size}")
        println("Symboles : ${grid.symbolCount}")
        check(grid.size == 12) { "Échec : taille=${grid.size} attendue 12x12" }
        println("PASS 12x12")
    }
}