package com.nextup.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.nextup.app.settings.SettingsRepository

object NotificationHelper {
    const val CHANNEL_ID = "nextup_task_alarms"

    /**
     * Notification channels are immutable once created on API 26+ — a Notification-level
     * setSound() call is silently ignored, only the channel's own sound is honored. So
     * whenever the user picks a different alarm sound, the channel has to be deleted and
     * recreated with the new sound for the change to actually take effect.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.deleteNotificationChannel(CHANNEL_ID)

            val soundUri = SettingsRepository(context).alarmSoundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarms for tasks you marked 'with alarm'"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

