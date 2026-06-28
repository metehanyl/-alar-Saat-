package com.metehanyl.calarsaat.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import java.util.Calendar

sealed class SabahNamaziResult {
    data class Success(val times: List<Pair<Int, Int>>) : SabahNamaziResult()
    object NoData : SabahNamaziResult()
}

/**
 * Reads today's İmsak time from the Ezan Vakti app's ContentProvider and schedules
 * 3 one-time alarms at imsak+10, imsak+14 and imsak+18 minutes, all using the Klasik
 * Çalar Saat melody and the app's normal PIN-dismiss flow. Re-run daily (see
 * [SabahNamaziRefreshReceiver]) since İmsak shifts by a minute or so each day.
 */
class SabahNamaziManager(private val context: Context) {

    private val dao by lazy { AlarmDatabase.getInstance(context).alarmDao() }
    private val scheduler by lazy { AlarmScheduler(context) }

    suspend fun refresh(): SabahNamaziResult {
        val imsak = queryImsakTime() ?: return SabahNamaziResult.NoData

        cancelAutoAlarms()

        val base = nextOccurrenceOf(imsak.hour, imsak.minute)
        val times = mutableListOf<Pair<Int, Int>>()
        for (offsetMinutes in OFFSETS_MINUTES) {
            val cal = base.clone() as Calendar
            cal.add(Calendar.MINUTE, offsetMinutes)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            val alarm = AlarmEntity(
                hour = hour,
                minute = minute,
                label = LABEL,
                soundId = 0,
                isAutoSabahNamazi = true
            )
            val id = dao.insert(alarm)
            val saved = alarm.copy(id = id.toInt())
            val nextTrigger = scheduler.schedule(saved)
            dao.update(saved.copy(nextTriggerAtMillis = nextTrigger))
            times.add(hour to minute)
        }
        return SabahNamaziResult.Success(times)
    }

    suspend fun cancelAutoAlarms() {
        for (alarm in dao.getAutoSabahNamaziAlarms()) {
            scheduler.cancel(alarm)
            dao.delete(alarm)
        }
    }

    fun scheduleDailyRefresh() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next.timeInMillis, dailyRefreshPendingIntent()
        )
    }

    fun cancelDailyRefresh() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(dailyRefreshPendingIntent())
    }

    private fun dailyRefreshPendingIntent(): PendingIntent {
        val intent = Intent(context, SabahNamaziRefreshReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REFRESH_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next datetime (today if still ahead, otherwise tomorrow) matching this wall-clock hour/minute. */
    private fun nextOccurrenceOf(hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (candidate.timeInMillis <= now.timeInMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate
    }

    private fun queryImsakTime(): ImsakTime? {
        val cursor = try {
            context.contentResolver.query(IMSAK_URI, null, null, null, null)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val imsakIndex = it.getColumnIndex("imsak")
            if (imsakIndex == -1) return null
            val raw = it.getString(imsakIndex) ?: return null
            val parts = raw.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].trim().toIntOrNull() ?: return null
            val minute = parts[1].trim().toIntOrNull() ?: return null
            return ImsakTime(hour, minute)
        }
    }

    private data class ImsakTime(val hour: Int, val minute: Int)

    companion object {
        const val LABEL = "Sabah Namazı"
        private val OFFSETS_MINUTES = intArrayOf(10, 14, 18)
        private const val REFRESH_REQUEST_CODE = 987654321
        private val IMSAK_URI: Uri = Uri.parse("content://com.metehanyl.ezanvakti.provider/imsak")
    }
}
