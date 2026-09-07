package com.meowdoku.solver.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meowdoku.solver.MeowdokuApp
import com.meowdoku.solver.R
import com.meowdoku.solver.solver.SolutionProcessor
import com.meowdoku.solver.ui.CaptureConsentActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Service de premier plan (type mediaProjection) qui capture l'écran après
 * consentement, résout la grille et transmet le résultat à l'overlay via
 * [resultListener].
 *
 * La VirtualDisplay est créée UNE seule fois à l'obtention du MediaProjection
 * et reste vivante tant que l'overlay est ouvert : Android 14+ interdit de
 * recréer une VirtualDisplay plusieurs fois avec la même projection. La
 * première capture est différée pour laisser le jeu reprendre l'écran après
 * la fenêtre de consentement (sinon le screenshot montre l'app, pas le jeu).
 */
class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"
        private const val NOTIFICATION_ID = 3001

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        /** Délai après le consentement pour laisser le jeu revenir au premier plan. */
        private const val FIRST_CAPTURE_DELAY_MS = 1500L
        /** Petit délai pour laisser l'écran se stabiliser entre deux captures. */
        private const val CAPTURE_DELAY_MS = 300L

        /** Chemin de la solution (null en cas d'échec). */
        @Volatile
        var resultListener: ((String?) -> Unit)? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        private val busy = AtomicBoolean(false)

        @Volatile
        private var appContext: Context? = null

        @Volatile
        private var projection: MediaProjection? = null

        @Volatile
        private var captureHandler: Handler? = null

        private var captureThread: HandlerThread? = null

        @Volatile
        private var reader: ImageReader? = null

        @Volatile
        private var virtualDisplay: VirtualDisplay? = null

        @Volatile
        private var captureWidth = 0

        @Volatile
        private var captureHeight = 0

        /** Dernière frame conservée par le listener, accessible à la capture. */
        @Volatile
        private var lastFrame: Bitmap? = null

        /** Compteur incrémenté à chaque nouvelle frame conservée. */
        private val lastFrameVersion = AtomicLong(0L)

        /** Démarre le service avec le résultat du consentement MediaProjection. */
        fun enqueue(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Arrête le service et libère la projection. */
        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureService::class.java))
        }

        /** Capture l'écran et résout. Re-demande le consentement si absent. */
        fun triggerCapture(context: Context) {
            if (projection == null) {
                Log.d(TAG, "Projection absente → demande de consentement")
                openConsentActivity(context)
                return
            }
            Log.d(TAG, "Projection active → capture directe")
            scheduleCapture(CAPTURE_DELAY_MS)
        }

        private fun openConsentActivity(context: Context) {
            // MULTIPLE_TASK : crée une tâche vierge pour que l'activité transparente
            // ne réutilise pas la tâche existante de l'app (sinon MainActivity se
            // retrouverait en dessous et apparaîtrait après le consentement).
            val intent = Intent(context, CaptureConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            context.startActivity(intent)
        }

        private fun scheduleCapture(delayMs: Long) {
            if (!busy.compareAndSet(false, true)) {
                return
            }
            Thread {
                try {
                    if (delayMs > 0) {
                        Thread.sleep(delayMs)
                    }
                    runCapture()
                } finally {
                    busy.set(false)
                }
            }.start()
        }

        private fun runCapture() {
            val context = appContext ?: run {
                mainHandler.post { resultListener?.invoke(null) }
                return
            }
            val result = try {
                val bitmap = acquireLatestImage()
                if (bitmap == null) {
                    Log.e(TAG, "Capture d'écran vide")
                    null
                } else {
                    try {
                        SolutionProcessor.solveAndSave(context, bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur de résolution", e)
                        // Le bitmap source est déjà recyclé par solveAndSave → ré-acquiert une frame fraîche.
                        val dump = acquireLatestImage()
                        if (dump != null) dumpCaptureForInspection(context, dump)
                        null
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur de capture", e)
                null
            }

            mainHandler.post {
                resultListener?.invoke(result)
            }
        }

        /** Sauvegarde le bitmap brut capturé pour diagnostic (dimensions + pixels). */
        private fun dumpCaptureForInspection(context: Context, bitmap: Bitmap) {
            try {
                val dir = File(context.cacheDir, "capture_dumps").apply { mkdirs() }
                val file = File(dir, "capture_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.d(TAG, "Capture échouée sauvegardée : ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
            } catch (e: Exception) {
                Log.e(TAG, "dumpCaptureForInspection : ${e.message}")
            }
        }

        /** Récupère la frame la plus récente conservée par le listener. */
        private fun acquireLatestImage(): Bitmap? {
            val before = lastFrameVersion.get()
            // Attend une nouvelle frame (le listener la rafraîchit en continu).
            val maxWait = 800L
            val start = System.currentTimeMillis()
            while (lastFrameVersion.get() == before &&
                System.currentTimeMillis() - start < maxWait
            ) {
                Thread.sleep(20L)
            }
            return lastFrame?.let { it.copy(Bitmap.Config.ARGB_8888, false) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildCaptureNotification())

        if (appContext == null) {
            appContext = applicationContext
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (intent == null) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = try {
                mgr.getMediaProjection(resultCode, resultData)
            } catch (e: Exception) {
                Log.e(TAG, "getMediaProjection : ${e.message}")
                null
            }
            if (mp == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            projection = mp
            registerProjectionCallback(mp)
            if (!setupCaptureSession(mp)) {
                mp.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            // Attendre que le jeu reprenne l'écran après la fenêtre de consentement.
            scheduleCapture(FIRST_CAPTURE_DELAY_MS)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun setupCaptureSession(mp: MediaProjection): Boolean {
        return try {
            val wm = getSystemService(WindowManager::class.java)
            val size = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(size)
            if (size.x == 0) size.x = resources.displayMetrics.widthPixels
            if (size.y == 0) size.y = resources.displayMetrics.heightPixels

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = size.x
            val height = size.y

            val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 4)
            val thread = HandlerThread("capture").apply { start() }
            val handler = Handler(thread.looper)

            // Consomme les frames en continu et conserve la plus récente dans
            // lastFrame pour les captures ultérieures.
            newReader.setOnImageAvailableListener({ r ->
                try {
                    val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val width = captureWidth
                    val height = captureHeight
                    val rowPadding = rowStride - pixelStride * width
                    val padded = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    padded.copyPixelsFromBuffer(buffer)
                    val bmp = if (rowPadding > 0) {
                        Bitmap.createBitmap(padded, 0, 0, width, height)
                    } else {
                        padded
                    }
                    if (bmp !== padded) padded.recycle()
                    val old = lastFrame
                    lastFrame = bmp
                    old?.let { if (!it.isRecycled) it.recycle() }
                    lastFrameVersion.incrementAndGet()
                    image.close()
                } catch (e: Exception) {
                    Log.e(TAG, "onImageAvailable : ${e.message}")
                }
            }, handler)

            val vd = mp.createVirtualDisplay(
                "meowdoku-capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface,
                null,
                null
            )

            captureThread = thread
            captureHandler = handler
            reader = newReader
            virtualDisplay = vd
            captureWidth = width
            captureHeight = height
            true
        } catch (e: Exception) {
            Log.e(TAG, "setupCaptureSession : ${e.message}")
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureHandler?.post { reader?.close() }
        virtualDisplay?.release()
        captureThread?.quitSafely()
        projection?.stop()
        captureHandler = null
        virtualDisplay = null
        reader = null
        captureThread = null
        projection = null
        lastFrame = null
    }

    /**
     * Android 14+ exige un callback MediaProjection enregistré avant de créer
     * une VirtualDisplay, sinon la capture échoue. On enregistre aussi un onStop
     * pour invalider la projection si l'utilisateur l'arrête.
     */
    private fun registerProjectionCallback(mp: MediaProjection) {
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection arrêtée → projection invalidée")
                projection = null
            }
        }
        @Suppress("DEPRECATION")
        mp.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun buildCaptureNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, MeowdokuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.capture_active_title))
            .setContentText(getString(R.string.capture_active_text))
            .setOngoing(true)
            .build()
    }
}