package com.meowdoku.solver.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.meowdoku.solver.R
import com.meowdoku.solver.service.OverlayService

/**
 * Affiche la grille résolue en plein écran, avec un bouton pour revenir en overlay.
 */
class SolutionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageView = ImageView(this).apply {
            setBackgroundColor(getColor(R.color.backdrop))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val backButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_exit_fullscreen)
            contentDescription = getString(R.string.overlay_back)
            setBackgroundColor(0xCC000000.toInt())
        }

        val params = FrameLayout.LayoutParams(
            (48 * resources.displayMetrics.density).toInt(),
            (48 * resources.displayMetrics.density).toInt()
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                0,
                0
            )
        }

        val root = FrameLayout(this).apply {
            addView(imageView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(backButton, params)
        }
        setContentView(root)

        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (path != null) {
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
            }
            backButton.setOnClickListener {
                OverlayService.enqueue(this, path)
                finish()
            }
        }
    }
}
