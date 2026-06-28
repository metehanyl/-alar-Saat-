package com.metehanyl.calarsaat.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.metehanyl.calarsaat.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SabahNamaziRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PrefsManager(context)
        if (!prefs.isSabahNamaziEnabled()) return

        val pendingResult = goAsync()
        val manager = SabahNamaziManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                manager.refresh()
            } finally {
                manager.scheduleDailyRefresh()
                pendingResult.finish()
            }
        }
    }
}
