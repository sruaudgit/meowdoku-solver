package com.meowdoku.solver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MeowdokuApp : Application() {

    companion object {
        const val CHANNEL_ID = "meowdoku_solutions"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
