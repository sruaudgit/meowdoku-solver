package com.meowdoku.solver.service

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meowdoku.solver.MeowdokuApp
import com.meowdoku.solver.R
import com.meowdoku.solver.detector.GridDetector
import com.meowdoku.solver.notification.NotificationHelper
import com.meowdoku.solver.solver.GridSolver
import com.meowdoku.solver.ui.GridRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * ForegroundService dédié : exécute le pipeline de résolution (lecture de
 * l'image partagée, détection, résolution, rendu, notification) avec une
 * priorité élevée pour éviter d'être gelé/tué par le gestionnaire mémoire
 * Samsung pendant qu'il est en arrière-plan.
 *
 * Déclenché directement depuis ShareReceiverActivity (activité visible),
 * ce qui garantit que startForeground() est autorisé sur Android 14+.
 */
class SolveService : Service() {

    companion object {
        private const val TAG = "SolveService"
        private const val TARGET_WIDTH = 1080
        private const val FOREGROUND_NOTIFICATION_ID = 2001

        const val KEY_IMAGE_PATH = "image_path"

        /** Démarre le service en premier plan avec un chemin d'image local. */
        fun enqueue(context: android.content.Context, imagePath: String) {
            val intent = Intent(context, SolveService::class.java)
                .putExtra(KEY_IMAGE_PATH, imagePath)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            FOREGROUND_NOTIFICATION_ID,
            buildAnalyzingNotification()
        )

        val path = intent?.getStringExtra(KEY_IMAGE_PATH)
        if (path == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Thread {
            solve(path)
            stopSelf(startId)
        }.start()

        return START_NOT_STICKY
    }

    private fun solve(path: String) {
        try {
            Log.d(TAG, "Début du traitement : $path")
            val sourceBitmap = loadBitmap(File(path))
            if (sourceBitmap == null) {
                Log.e(TAG, "Impossible de décoder l'image : $path")
                NotificationHelper.showErrorNotification(this, "Impossible de lire l'image.")
                return
            }
            Log.d(TAG, "Image chargée : ${sourceBitmap.width}x${sourceBitmap.height}")

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

            Log.d(TAG, "Détection en cours…")
            val grid = GridDetector.detect(pixels, width, height)
            Log.d(TAG, "Grille détectée : ${grid.size}x${grid.size}, ${grid.symbolCount} symbole(s), ${grid.colorMap.size} couleurs")

            val fixedSymbols = GridSolver.collectSymbols(grid)
            Log.d(TAG, "Résolution en cours…")
            val solution = GridSolver.findSolution(grid, fixedSymbols)
                ?: throw GridSolver.ConsistencyException("Aucune solution trouvée avec les symboles posés.")
            Log.d(TAG, "Solution trouvée avec ${solution.size} symboles")

            val solutionBmp = GridRenderer.renderSolution(grid, solution)

            val solutionsDir = File(cacheDir, "solutions").apply { mkdirs() }
            val solutionFile = File(solutionsDir, "solution_${System.currentTimeMillis()}.png")
            saveBitmap(solutionBmp, solutionFile)
            val thumbFile = File(solutionsDir, "thumb_${System.currentTimeMillis()}.png")
            saveThumbnail(solutionBmp, thumbFile)
            solutionBmp.recycle()

            NotificationHelper.showSolutionNotification(
                this,
                solutionFile.absolutePath,
                thumbFile.absolutePath
            )
            Log.d(TAG, "Terminé avec succès.")
        } catch (e: GridDetector.DetectionException) {
            Log.e(TAG, "Erreur de détection", e)
            NotificationHelper.showErrorNotification(this, e.message ?: "Détection impossible.")
        } catch (e: GridSolver.ConsistencyException) {
            Log.e(TAG, "Erreur de résolution", e)
            NotificationHelper.showErrorNotification(this, e.message ?: "Résolution impossible.")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur inattendue", e)
            NotificationHelper.showErrorNotification(this, "Erreur : ${e.message}")
        }
    }

    private fun buildAnalyzingNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, MeowdokuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.analyzing))
            .setOngoing(true)
            .build()
    }

    private fun loadBitmap(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= TARGET_WIDTH) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmap(bmp: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }

    private fun saveThumbnail(bmp: Bitmap, file: File): Boolean {
        val thumbW = 256
        val thumbH = 256
        val thumb = Bitmap.createScaledBitmap(bmp, thumbW, thumbH, false)
        val ok = try {
            FileOutputStream(file).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            if (thumb !== bmp) thumb.recycle()
        }
        return ok
    }
}
