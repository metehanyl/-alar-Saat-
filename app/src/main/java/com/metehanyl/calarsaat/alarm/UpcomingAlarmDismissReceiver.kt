package com.metehanyl.calarsaat.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.metehanyl.calarsaat.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Kullanıcı "Sonraki Alarm" bildirimini kapatırsa bu receiver tetiklenir ve
 * bildirimi hemen yeniden gösterir — bildirimin her zaman görünür kalmasını sağlar.
 */
class UpcomingAlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            val alarms = AlarmDatabase.getInstance(context).alarmDao().getAll()
            UpcomingAlarmNotifier(context).update(alarms)
        }
    }
}
