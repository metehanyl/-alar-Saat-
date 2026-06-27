package com.metehanyl.calarsaat.util

import java.util.Calendar

/** Days in display order (Monday first), each paired with its Calendar.DAY_OF_WEEK value. */
object DayUtils {

    val orderedDays: List<Pair<Int, String>> = listOf(
        Calendar.MONDAY to "Pzt",
        Calendar.TUESDAY to "Sal",
        Calendar.WEDNESDAY to "Çar",
        Calendar.THURSDAY to "Per",
        Calendar.FRIDAY to "Cum",
        Calendar.SATURDAY to "Cmt",
        Calendar.SUNDAY to "Paz"
    )

    fun summarize(days: Set<Int>): String {
        if (days.isEmpty()) return "Bir kez"
        if (days.size == 7) return "Her gün"
        val weekdays = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        if (days == weekdays) return "Hafta içi"
        if (days == weekend) return "Hafta sonu"
        return orderedDays.filter { it.first in days }.joinToString(", ") { it.second }
    }
}
