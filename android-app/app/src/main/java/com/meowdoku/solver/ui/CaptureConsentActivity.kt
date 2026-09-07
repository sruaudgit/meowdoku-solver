package com.meowdoku.solver.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.meowdoku.solver.R
import com.meowdoku.solver.service.CaptureService

/**
 * Activity transparente : demande le consentement de capture d'écran
 * (MediaProjection), un dialogue système s'affiche au-dessus du jeu.
 * Une fois accordé, démarre [CaptureService] puis se ferme.
 */
class CaptureConsentActivity : ComponentActivity() {

    private companion object {
        const val REQUEST_CAPTURE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                CaptureService.enqueue(this, resultCode, data)
            } else {
                Toast.makeText(this, R.string.capture_denied, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}