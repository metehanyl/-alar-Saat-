package com.metehanyl.calarsaat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    // Comma separated Calendar.DAY_OF_WEEK values (1=Sunday .. 7=Saturday). Empty = one-time alarm.
    val repeatDays: String = "",
    val enabled: Boolean = true,
    // Id of the AlarmMelody (see AlarmSounds) used when this alarm rings.
    val soundId: Int = 0,
    // epoch millis of the next time this alarm is scheduled to fire, used to recompute on boot
    val nextTriggerAtMillis: Long = 0L
) {
    fun repeatDaysSet(): Set<Int> =
        if (repeatDays.isBlank()) emptySet()
        else repeatDays.split(",").map { it.trim().toInt() }.toSet()

    fun isRepeating(): Boolean = repeatDays.isNotBlank()

    companion object {
        fun daysToString(days: Set<Int>): String = days.sorted().joinToString(",")
    }
}
