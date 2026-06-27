package com.metehanyl.calarsaat.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.metehanyl.calarsaat.data.AlarmEntity
import java.util.Calendar

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_ALARM_LABEL = "extra_alarm_label"

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmEntity): Long {
        val triggerAt = computeNextTriggerMillis(alarm)
        val pendingIntent = buildPendingIntent(alarm)

        val showIntent = PendingIntent.getActivity(
            context, alarm.id, Intent(context, com.metehanyl.calarsaat.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
        alarmManager.setAlarmClock(info, pendingIntent)
        return triggerAt
    }

    fun cancel(alarm: AlarmEntity) {
        alarmManager.cancel(buildPendingIntent(alarm))
    }

    private fun buildPendingIntent(alarm: AlarmEntity): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
        }
        return PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Computes the next epoch-millis trigger time for this alarm's hour/minute/repeat days. */
    fun computeNextTriggerMillis(alarm: AlarmEntity): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val repeatDays = alarm.repeatDaysSet()
        if (repeatDays.isEmpty()) {
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        // Repeating alarm: find the next matching day at/after now.
        for (i in 0..7) {
            val attempt = candidate.clone() as Calendar
            attempt.add(Calendar.DAY_OF_YEAR, i)
            val dow = attempt.get(Calendar.DAY_OF_WEEK)
            if (dow in repeatDays && attempt.timeInMillis > now.timeInMillis) {
                return attempt.timeInMillis
            }
        }
        // Fallback (shouldn't happen): one week from candidate
        candidate.add(Calendar.DAY_OF_YEAR, 7)
        return candidate.timeInMillis
    }
}
