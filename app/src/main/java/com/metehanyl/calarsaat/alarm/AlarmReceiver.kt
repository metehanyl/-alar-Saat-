package com.metehanyl.calarsaat.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.metehanyl.calarsaat.data.AlarmDatabase
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
        if (alarmId == -1) return

        val ringIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_ALARM_SOUND_ID, soundId)
            putExtra(EXTRA_ALARM_REQUIRE_PIN, requirePin)
            putExtra(EXTRA_ALARM_PIN_HASH, pinHash)
        }
        ContextCompat.startForegroundService(context, ringIntent)

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
}
