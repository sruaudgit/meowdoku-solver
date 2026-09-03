package com.meowdoku.solver.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.meowdoku.solver.R
import com.meowdoku.solver.service.SolveService
import java.io.File
import java.io.FileOutputStream

/**
 * Reçoit une image partagée (ACTION_SEND) depuis le système et lance le
 * traitement d'arrière-plan, puis se ferme immédiatement.
 *
 * L'URI partagé est copié dans le cache de l'app immédiatement (la permission
 * de lecture accordée par le système ne vit que le temps de cette activity),
 * puis le chemin local est passé au worker.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri: Uri? = if (intent?.action == Intent.ACTION_SEND) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else {
            null
        }

        if (imageUri != null) {
            val localFile = copyToCache(imageUri)
            if (localFile != null) {
                SolveService.enqueue(this, localFile.absolutePath)
                Toast.makeText(this, R.string.analyzing, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Impossible de lire l'image partagée.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Aucune image reçue.", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun copyToCache(uri: Uri): File? {
        return try {
            val incomingDir = File(cacheDir, "incoming").apply { mkdirs() }
            val dest = File(incomingDir, "shared_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            dest
        } catch (e: Exception) {
            null
        }
    }
}
