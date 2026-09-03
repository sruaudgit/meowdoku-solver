package com.meowdoku.solver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.meowdoku.solver.R

/**
 * Écran d'accueil : explique comment utiliser l'app par partage.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        val padding = (32 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(getColor(R.color.backdrop))
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTextColor(getColor(R.color.primary))
            setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
        }

        val body = TextView(this).apply {
            text = "1. Prenez une capture d'écran de la grille du jeu Meowdoku.\n" +
                "2. Depuis votre galerie photo, ouvrez l'image et choisissez \"Partager\".\n" +
                "3. Sélectionnez Meowdoku Solver dans la liste des applications.\n\n" +
                "Une notification s'affichera alors avec une image de la grille résolue.\n" +
                "Touchez la notification pour voir la solution en superposition par-dessus le jeu."
            textSize = 15f
            setTextColor(getColor(R.color.white))
            setLineSpacing(0f, 1.3f)
        }

        layout.addView(title)
        layout.addView(body)
        setContentView(layout)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    42
                )
            }
        }
    }
}
