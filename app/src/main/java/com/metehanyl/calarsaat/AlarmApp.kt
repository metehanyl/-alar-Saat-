package com.metehanyl.calarsaat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build

class AlarmApp : Application() {

    companion object {
        const val CHANNEL_ID_ALARM = "alarm_channel"
        const val CHANNEL_ID_SNOOZE = "snooze_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationManager.IMPORTANCE_HIGH
            }
            val snoozeChannel = NotificationChannel(
                CHANNEL_ID_SNOOZE,
                "Erteleme Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(snoozeChannel)
        }
    }
}
