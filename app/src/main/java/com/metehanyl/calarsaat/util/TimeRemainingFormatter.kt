package com.metehanyl.calarsaat.util

import android.content.Context
import com.metehanyl.calarsaat.R
import java.util.Calendar

/** Computes and formats how long until an hour/minute (optionally repeating) next fires. */
object TimeRemainingFormatter {

    /** Next epoch-millis trigger for this hour/minute, given the days it repeats on (empty = one-time). */
    fun nextTriggerMillis(hour: Int, minute: Int, repeatDays: Set<Int> = emptySet()): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty()) {
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        for (i in 0..7) {
            val attempt = candidate.clone() as Calendar
            attempt.add(Calendar.DAY_OF_YEAR, i)
            val dow = attempt.get(Calendar.DAY_OF_WEEK)
            if (dow in repeatDays && attempt.timeInMillis > now.timeInMillis) {
                return attempt.timeInMillis
            }
        }
        candidate.add(Calendar.DAY_OF_YEAR, 7)
        return candidate.timeInMillis
    }

    fun format(context: Context, hour: Int, minute: Int, repeatDays: Set<Int> = emptySet()): String {
        val totalMinutes = ((nextTriggerMillis(hour, minute, repeatDays) - System.currentTimeMillis()) / 60_000L)
            .coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.time_remaining_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.time_remaining_minutes, minutes)
        }
    }
}
