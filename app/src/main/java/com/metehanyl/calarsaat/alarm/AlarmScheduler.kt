package com.metehanyl.calarsaat.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.util.TimeRemainingFormatter

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_ALARM_LABEL = "extra_alarm_label"
const val EXTRA_ALARM_SOUND_ID = "extra_alarm_sound_id"
const val EXTRA_ALARM_REQUIRE_PIN = "extra_alarm_require_pin"
const val EXTRA_ALARM_PIN_HASH = "extra_alarm_pin_hash"
const val EXTRA_ALARM_VOLUME = "extra_alarm_volume"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"
const val EXTRA_ALARM_LOCK_TYPE = "extra_alarm_lock_type"
const val EXTRA_ALARM_PATTERN_HASH = "extra_alarm_pattern_hash"
const val EXTRA_ALARM_TEXT_PASS_HASH = "extra_alarm_text_pass_hash"
const val EXTRA_ALARM_LOCK_STEPS = "extra_alarm_lock_steps"

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

    fun scheduleSnooze(
        alarmId: Int,
        label: String,
        soundId: Int,
        requirePin: Boolean,
        pinHash: String?,
        volume: Int = 100,
        lockType: String = "",
        patternHash: String? = null,
        textPassHash: String? = null,
        lockSteps: String = "",
        minutes: Int = SNOOZE_MINUTES
    ): Long {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val requestCode = snoozeRequestCode(alarmId)

        val effectiveSteps = lockSteps.ifEmpty { lockType }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_ALARM_SOUND_ID, soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, pinHash)
            putExtra(EXTRA_ALARM_VOLUME, volume)
            putExtra(EXTRA_ALARM_LOCK_TYPE, lockType)
            putExtra(EXTRA_ALARM_PATTERN_HASH, patternHash)
            putExtra(EXTRA_ALARM_TEXT_PASS_HASH, textPassHash)
            putExtra(EXTRA_ALARM_LOCK_STEPS, effectiveSteps)
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

    fun cancelSnooze(alarmId: Int) {
        val requestCode = snoozeRequestCode(alarmId)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun snoozeRequestCode(alarmId: Int) = alarmId + SNOOZE_REQUEST_CODE_OFFSET

    private fun buildPendingIntent(alarm: AlarmEntity): PendingIntent {
        val steps = alarm.effectiveLockSteps().joinToString(",")
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_SOUND_ID, alarm.soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, alarm.requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, alarm.pinHash)
            putExtra(EXTRA_ALARM_VOLUME, alarm.volume)
            putExtra(EXTRA_ALARM_LOCK_TYPE, alarm.effectiveLockType())
            putExtra(EXTRA_ALARM_PATTERN_HASH, alarm.patternHash)
            putExtra(EXTRA_ALARM_TEXT_PASS_HASH, alarm.textPassHash)
            putExtra(EXTRA_ALARM_LOCK_STEPS, steps)
        }
        return PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun computeNextTriggerMillis(alarm: AlarmEntity): Long =
        TimeRemainingFormatter.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysSet())

    companion object {
        const val SNOOZE_MINUTES = 5
        const val SNOOZE_REQUEST_CODE_OFFSET = 1_000_000
        const val SNOOZE_NOTIF_ID_OFFSET = 2_000_000
    }
}
