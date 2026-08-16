package com.metehanyl.calarsaat.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.PrefsManager
import com.metehanyl.calarsaat.prayer.PrayerRepository
import com.metehanyl.calarsaat.prayer.RefreshResult
import java.util.Calendar

sealed class SabahNamaziResult {
    data class Success(
        val times: List<Pair<Int, Int>>,
        val sehirAdi: String,
        val ilceAdi: String
    ) : SabahNamaziResult()
    data class Failure(val message: String) : SabahNamaziResult()
}

/**
 * GPS konumundan otomatik olarak çözümlenen bugünün İmsak vaktine göre kullanıcının ayarladığı
 * sayıda one-time alarm kurar (varsayılan: imsak+10, +14 ve +18 dakika), Klasik Çalar Saat
 * melodisiyle ve isteğe bağlı PIN-kapatma akışıyla. Günlük olarak yeniden çalıştırılır (bkz.
 * [SabahNamaziRefreshReceiver]) çünkü İmsak her gün birkaç dakika kayar.
 */
class SabahNamaziManager(private val context: Context) {

    private val dao by lazy { AlarmDatabase.getInstance(context).alarmDao() }
    private val scheduler by lazy { AlarmScheduler(context) }
    private val prayerRepository by lazy { PrayerRepository(context) }
    private val prefs by lazy { PrefsManager(context) }

    suspend fun refresh(): SabahNamaziResult {
        val imsakLookup = resolveTodaysImsak()
        val imsak: ImsakTime
        val sehirAdi: String
        val ilceAdi: String
        when (imsakLookup) {
            is ImsakLookup.Found -> {
                imsak = imsakLookup.time
                sehirAdi = imsakLookup.sehirAdi
                ilceAdi = imsakLookup.ilceAdi
            }
            is ImsakLookup.NotFound -> return SabahNamaziResult.Failure(imsakLookup.message)
        }

        cancelAutoAlarms()

        val base = nextOccurrenceOf(imsak.hour, imsak.minute)
        val count = prefs.getSabahNamaziAlarmCount().coerceIn(1, 10)
        val interval = prefs.getSabahNamaziIntervalMinutes().coerceIn(1, 60)
        val offset = prefs.getSabahNamaziOffsetMinutes().coerceIn(0, 120)
        val volume = prefs.getSabahNamaziVolume()

        val lockSteps = run {
            val saved = prefs.getSabahNamaziLockSteps()
            if (saved.isNotEmpty()) saved
            else if (prefs.isSabahNamaziPinRequired()) "pin"
            else ""
        }
        val pinHash = if ("pin" in lockSteps.split(",")) prefs.getSabahNamaziPinHash() else null
        val patternHash = if ("pattern" in lockSteps.split(",")) prefs.getSabahNamaziPatternHash() else null
        val textPassHash = if ("text" in lockSteps.split(",")) prefs.getSabahNamaziTextPassHash() else null

        val times = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until count) {
            val cal = base.clone() as Calendar
            cal.add(Calendar.MINUTE, offset + i * interval)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            val alarm = AlarmEntity(
                hour = hour,
                minute = minute,
                label = LABEL,
                soundId = 0,
                isAutoSabahNamazi = true,
                requirePin = pinHash != null,
                pinHash = pinHash,
                volume = volume,
                lockSteps = lockSteps,
                patternHash = patternHash,
                textPassHash = textPassHash
            )
            val id = dao.insert(alarm)
            val saved = alarm.copy(id = id.toInt())
            val nextTrigger = scheduler.schedule(saved)
            dao.update(saved.copy(nextTriggerAtMillis = nextTrigger))
            times.add(hour to minute)
        }
        return SabahNamaziResult.Success(times, sehirAdi, ilceAdi)
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

    private suspend fun resolveTodaysImsak(): ImsakLookup {
        val cached = prayerRepository.getCachedBundle()
        if (cached != null) {
            val (day, isToday) = cached.todayOrClosest()
            if (isToday) {
                return parseImsak(day.imsak)?.let {
                    ImsakLookup.Found(it, cached.sehirAdi, cached.ilceAdi)
                } ?: ImsakLookup.NotFound("İmsak vakti okunamadı.")
            }
        }
        return when (val result = prayerRepository.refresh()) {
            is RefreshResult.Success -> {
                val (day, isToday) = result.bundle.todayOrClosest()
                if (!isToday) {
                    ImsakLookup.NotFound("Bugünün İmsak vakti alınamadı.")
                } else {
                    parseImsak(day.imsak)?.let {
                        ImsakLookup.Found(it, result.bundle.sehirAdi, result.bundle.ilceAdi)
                    } ?: ImsakLookup.NotFound("İmsak vakti okunamadı.")
                }
            }
            is RefreshResult.Failure -> ImsakLookup.NotFound(result.message)
        }
    }

    private fun parseImsak(raw: String): ImsakTime? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        return ImsakTime(hour, minute)
    }

    private sealed class ImsakLookup {
        data class Found(val time: ImsakTime, val sehirAdi: String, val ilceAdi: String) : ImsakLookup()
        data class NotFound(val message: String) : ImsakLookup()
    }

    private data class ImsakTime(val hour: Int, val minute: Int)

    companion object {
        const val LABEL = "Sabah Namazı"
        private const val REFRESH_REQUEST_CODE = 987654321
    }
}
