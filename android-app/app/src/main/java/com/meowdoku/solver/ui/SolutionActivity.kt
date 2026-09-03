package com.meowdoku.solver.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.meowdoku.solver.R

/**
 * Affiche la grille résolue en plein écran.
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
        setContentView(imageView)

        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (path != null) {
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
            }
        }
    }
}
