package com.meowdoku.solver.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.meowdoku.solver.MeowdokuApp
import com.meowdoku.solver.R
import com.meowdoku.solver.ui.SolutionActivity

/**
 * Gère la création et l'affichage de la notification de solution.
 */
object NotificationHelper {

    fun showSolutionNotification(context: Context, solutionPath: String, thumbnailPath: String) {
        val title = context.getString(R.string.notification_title)
        val text = context.getString(R.string.notification_text)

        lateinit var largeIcon: Bitmap
        try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4
            }
            largeIcon = BitmapFactory.decodeFile(thumbnailPath, opts)
        } catch (e: Exception) {
            largeIcon = fallbackBitmap(context)
        }

        val fullSize = BitmapFactory.decodeFile(solutionPath)

        // PendingIntent qui ouvre SolutionActivity
        val openIntent = Intent(context, SolutionActivity::class.java)
            .putExtra(SolutionActivity.EXTRA_IMAGE_PATH, solutionPath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MeowdokuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(largeIcon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (fullSize != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(fullSize))
        }

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(MEOWDOKU_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notifications permission refusée - silencieux
        }
    }

    fun showErrorNotification(context: Context, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, MeowdokuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_error))
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        try {
            manager.notify(MEOWDOKU_ERROR_ID, builder.build())
        } catch (e: SecurityException) {
        }
    }

    private fun fallbackBitmap(context: Context): Bitmap {
        val fallback = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(fallback)
        canvas.drawColor(context.getColor(R.color.backdrop))
        return fallback
    }

    const val MEOWDOKU_NOTIFICATION_ID = 1001
    const val MEOWDOKU_ERROR_ID = 1002
}
