package com.metehanyl.calarsaat.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.metehanyl.calarsaat.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnoozeCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId == -1) return

        AlarmScheduler(context).cancelSnooze(alarmId)
        NotificationManagerCompat.from(context).cancel(alarmId + AlarmScheduler.SNOOZE_NOTIF_ID_OFFSET)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AlarmDatabase.getInstance(context).alarmDao()
                val alarm = dao.getById(alarmId)
                if (alarm != null) {
                    dao.update(alarm.copy(isSnoozed = false, snoozedUntilMillis = 0L))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
