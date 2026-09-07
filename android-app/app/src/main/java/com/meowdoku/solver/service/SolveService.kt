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
import com.meowdoku.solver.solver.SolutionProcessor
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

            Log.d(TAG, "Détection en cours…")
            val solutionPath = SolutionProcessor.solveAndSave(this, sourceBitmap)
            Log.d(TAG, "Solution trouvée : $solutionPath")

            val thumbFile = File(cacheDir, "solutions")
                .apply { mkdirs() }
                .resolve("thumb_${System.currentTimeMillis()}.png")
            saveThumbnail(solutionPath, thumbFile)

            NotificationHelper.showSolutionNotification(
                this,
                solutionPath,
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

    private fun saveThumbnail(solutionPath: String, file: File) {
        try {
            val full = BitmapFactory.decodeFile(solutionPath) ?: return
            val thumb = Bitmap.createScaledBitmap(full, 256, 256, false)
            if (thumb !== full) full.recycle()
            FileOutputStream(file).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            thumb.recycle()
        } catch (e: Exception) {
            // Miniature optionnelle : on ignore l'échec.
        }
    }
}
