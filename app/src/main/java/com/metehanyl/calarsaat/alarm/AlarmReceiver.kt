package com.metehanyl.calarsaat.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.ui.AlarmRingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL).orEmpty()
        val soundId = intent.getIntExtra(EXTRA_ALARM_SOUND_ID, 0)
        val requirePin = intent.getBooleanExtra(EXTRA_ALARM_REQUIRE_PIN, false)
        val pinHash = intent.getStringExtra(EXTRA_ALARM_PIN_HASH)
        val volume = intent.getIntExtra(EXTRA_ALARM_VOLUME, 100)
        val lockType = intent.getStringExtra(EXTRA_ALARM_LOCK_TYPE).orEmpty()
        val patternHash = intent.getStringExtra(EXTRA_ALARM_PATTERN_HASH)
        val textPassHash = intent.getStringExtra(EXTRA_ALARM_TEXT_PASS_HASH)
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        if (alarmId == -1) return

        wakeScreen(context)

        val ringIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_ALARM_SOUND_ID, soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, pinHash)
            putExtra(EXTRA_ALARM_VOLUME, volume)
            putExtra(EXTRA_ALARM_LOCK_TYPE, lockType)
            putExtra(EXTRA_ALARM_PATTERN_HASH, patternHash)
            putExtra(EXTRA_ALARM_TEXT_PASS_HASH, textPassHash)
        }
        ContextCompat.startForegroundService(context, ringIntent)

        val activityIntent = Intent(context, AlarmRingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_ALARM_SOUND_ID, soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, pinHash)
            putExtra(EXTRA_ALARM_VOLUME, volume)
            putExtra(EXTRA_ALARM_LOCK_TYPE, lockType)
            putExtra(EXTRA_ALARM_PATTERN_HASH, patternHash)
            putExtra(EXTRA_ALARM_TEXT_PASS_HASH, textPassHash)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        context.startActivity(activityIntent)

        if (isSnooze) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AlarmDatabase.getInstance(context).alarmDao()
                val alarm = dao.getById(alarmId)
                if (alarm != null) {
                    if (alarm.isRepeating()) {
                        val nextTrigger = AlarmScheduler(context).schedule(alarm)
                        dao.update(alarm.copy(nextTriggerAtMillis = nextTrigger))
                    } else {
                        dao.update(alarm.copy(enabled = false, nextTriggerAtMillis = 0L))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "CalarSaat:AlarmScreenWakeLock"
        )
        wakeLock.acquire(10_000L)
    }
}
