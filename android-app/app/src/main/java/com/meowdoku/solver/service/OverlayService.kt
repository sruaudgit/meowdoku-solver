package com.meowdoku.solver.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.meowdoku.solver.R
import kotlin.math.roundToInt

/**
 * Affiche la grille résolue dans une fenêtre flottante par-dessus les autres
 * applications (type "chat head"), via SYSTEM_ALERT_WINDOW.
 *
 * La fenêtre est créée depuis un Service (et non une Activity) pour que
 * l'application en dessous reste l'app de premier plan et continue de recevoir
 * les clics. Seule la surface de la grille capture les touches (drag / pincement),
 * le reste est transparent au toucher grâce à FLAG_NOT_TOUCH_MODAL.
 */
class OverlayService : Service() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"

        fun enqueue(context: Context, imagePath: String) {
            val intent = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_IMAGE_PATH, imagePath)
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var rootViewWidth = 0
    private var rootViewHeight = 0
    private var bitmap: Bitmap? = null

    // État du drag (suivi en coordonnées absolues)
    private var dragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downWinX = 0
    private var downWinY = 0
    private var maxX = 0
    private var maxY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (rootView != null) {
            // Déjà affiché : on l'ignore.
            return START_NOT_STICKY
        }

        val path = intent?.getStringExtra(EXTRA_IMAGE_PATH)
        val loaded = if (path == null) null else loadBitmap(path)
        if (loaded == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        bitmap = loaded

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = android.view.LayoutInflater.from(this)
        rootView = inflater.inflate(R.layout.overlay_view, null)
        rootView!!.findViewById<ImageView>(R.id.overlayImage).setImageBitmap(loaded)

        rootView!!.findViewById<View>(R.id.overlayClose).setOnClickListener { dismiss() }

        params = buildLayoutParams()
        try {
            windowManager.addView(rootView, params)
        } catch (e: Exception) {
            rootView = null
            stopSelf()
            return START_NOT_STICKY
        }

        rootView!!.post {
            rootViewWidth = rootView!!.width
            rootViewHeight = rootView!!.height
            val size = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(size)
            if (size.x == 0) size.x = resources.displayMetrics.widthPixels
            if (size.y == 0) size.y = resources.displayMetrics.heightPixels
            maxX = maxOf(0, size.x - rootViewWidth)
            maxY = maxOf(0, size.y - rootViewHeight)
            bindGestures()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeViewIfPresent()
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        bitmap = null
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            val full = BitmapFactory.decodeFile(path)
            if (full == null) {
                null
            } else {
                // Limite la taille en mémoire pour l'affichage flottant.
                val maxDim = 640
                if (full.width > maxDim || full.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(full.width, full.height)
                    val w = (full.width * scale).toInt().coerceAtLeast(1)
                    val h = (full.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(full, w, h, true)
                    if (scaled !== full) full.recycle()
                    scaled
                } else {
                    full
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val size = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(size)
        if (size.x == 0) size.x = resources.displayMetrics.widthPixels
        if (size.y == 0) size.y = resources.displayMetrics.heightPixels
        val width = (size.x * 0.4f).roundToInt().coerceIn(240, 700)
        val x = ((size.x - width) / 2).coerceAtLeast(0)
        val y = (size.y * 0.2f).toInt()

        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun bindGestures() {
        val closeView = rootView!!.findViewById<View>(R.id.overlayClose)

        rootView!!.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isTouchOnView(event, closeView)) {
                        val p = params ?: return@setOnTouchListener true
                        dragging = true
                        downRawX = event.rawX
                        downRawY = event.rawY
                        downWinX = p.x
                        downWinY = p.y
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        val p = params ?: return@setOnTouchListener true
                        val nx = (downWinX + (event.rawX - downRawX)).toInt().coerceIn(0, maxX)
                        val ny = (downWinY + (event.rawY - downRawY)).toInt().coerceIn(0, maxY)
                        if (p.x != nx || p.y != ny) {
                            p.x = nx
                            p.y = ny
                            windowManager.updateViewLayout(rootView, p)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> true
            }
        }
    }

    private fun isTouchOnView(event: MotionEvent, view: View): Boolean {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        val w = view.width
        val h = view.height
        return x in loc[0]..(loc[0] + w) && y in loc[1]..(loc[1] + h)
    }

    private fun removeViewIfPresent() {
        try {
            rootView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // Vue déjà retirée
        }
        rootView = null
    }

    private fun dismiss() {
        removeViewIfPresent()
        stopSelf()
    }
}
