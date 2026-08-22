package com.metehanyl.calarsaat.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.metehanyl.calarsaat.AlarmApp
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.ui.MainActivity
import java.util.Calendar

/**
 * Herhangi bir alarm etkinleştirildiğinde, durum çubuğunda "Sonraki Alarm: 07:30" şeklinde
 * silinemeyen (ongoing) bir bildirim gösterir. Tüm alarmlar devre dışı kalırsa bildirimi kaldırır.
 */
class UpcomingAlarmNotifier(private val context: Context) {

    fun update(alarms: List<AlarmEntity>) {
        val nm = NotificationManagerCompat.from(context)

        val enabled = alarms.filter { it.enabled }
        if (enabled.isEmpty()) {
            nm.cancel(NOTIF_ID)
            return
        }

        val now = System.currentTimeMillis()
        val scheduler = AlarmScheduler(context)
        val next = enabled
            .mapNotNull { alarm ->
                val trigger = alarm.nextTriggerAtMillis.takeIf { it > now }
                    ?: scheduler.computeNextTriggerMillis(alarm)
                if (trigger > now) alarm to trigger else null
            }
            .minByOrNull { it.second }

        if (next == null) {
            nm.cancel(NOTIF_ID)
            return
        }

        val (alarm, triggerMs) = next
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        val timeStr = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val contentText = if (alarm.label.isNotBlank()) "$timeStr · ${alarm.label}" else timeStr

        val openPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Kullanıcı bildirimi kapatırsa UpcomingAlarmDismissReceiver hemen yeniden gösterir
        val dismissPi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, UpcomingAlarmDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, AlarmApp.CHANNEL_ID_UPCOMING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.upcoming_alarm_notif_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(openPi)
            .setDeleteIntent(dismissPi)
            .build()

        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // Bildirim izni verilmemiş
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIF_ID)

    companion object {
        const val NOTIF_ID = 1001
    }
}
