package com.metehanyl.calarsaat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interval_alarm_groups")
data class IntervalAlarmGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val intervalMinutes: Int = 10,
    val alarmCount: Int = 3,
    val repeatDays: String = "",
    val soundId: Int = 0,
    val volume: Int = 100,
    val enabled: Boolean = true,
    val lockSteps: String = "",
    val pinHash: String? = null,
    val patternHash: String? = null,
    val textPassHash: String? = null
) {
    fun repeatDaysSet(): Set<Int> =
        if (repeatDays.isBlank()) emptySet()
        else repeatDays.split(",").map { it.trim().toInt() }.toSet()

    fun lockStepsList(): List<String> =
        if (lockSteps.isEmpty()) emptyList()
        else lockSteps.split(",").filter { it.isNotEmpty() }
}
