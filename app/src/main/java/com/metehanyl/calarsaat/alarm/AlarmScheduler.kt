package com.metehanyl.calarsaat.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.metehanyl.calarsaat.data.AlarmEntity
import java.util.Calendar

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_ALARM_LABEL = "extra_alarm_label"
const val EXTRA_ALARM_SOUND_ID = "extra_alarm_sound_id"
const val EXTRA_ALARM_REQUIRE_PIN = "extra_alarm_require_pin"
const val EXTRA_ALARM_PIN_HASH = "extra_alarm_pin_hash"
const val EXTRA_ALARM_VOLUME = "extra_alarm_volume"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"

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

    /** Reschedules a ringing alarm to fire again after [minutes], independent of its normal repeat schedule. */
    fun scheduleSnooze(
        alarmId: Int,
        label: String,
        soundId: Int,
        requirePin: Boolean,
        pinHash: String?,
        volume: Int = 100,
        minutes: Int = SNOOZE_MINUTES
    ): Long {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val requestCode = snoozeRequestCode(alarmId)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_ALARM_SOUND_ID, soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, pinHash)
            putExtra(EXTRA_ALARM_VOLUME, volume)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context, requestCode, Intent(context, com.metehanyl.calarsaat.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pendingIntent)
        return triggerAt
    }

    private fun snoozeRequestCode(alarmId: Int) = alarmId + SNOOZE_REQUEST_CODE_OFFSET

    private fun buildPendingIntent(alarm: AlarmEntity): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_SOUND_ID, alarm.soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, alarm.requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, alarm.pinHash)
            putExtra(EXTRA_ALARM_VOLUME, alarm.volume)
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

    companion object {
        const val SNOOZE_MINUTES = 5
        private const val SNOOZE_REQUEST_CODE_OFFSET = 1_000_000
    }
}
