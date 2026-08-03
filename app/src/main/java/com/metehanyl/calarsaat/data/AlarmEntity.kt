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
    val repeatDays: String = "",
    val enabled: Boolean = true,
    val soundId: Int = 0,
    val nextTriggerAtMillis: Long = 0L,
    val isAutoSabahNamazi: Boolean = false,
    val groupId: Int? = null,
    val requirePin: Boolean = false,
    val pinHash: String? = null,
    val volume: Int = 100,
    val lockType: String = "",
    val patternHash: String? = null,
    val textPassHash: String? = null
) {
    fun repeatDaysSet(): Set<Int> =
        if (repeatDays.isBlank()) emptySet()
        else repeatDays.split(",").map { it.trim().toInt() }.toSet()

    fun isRepeating(): Boolean = repeatDays.isNotBlank()

    fun effectiveLockType(): String = when {
        lockType.isNotEmpty() -> lockType
        requirePin -> "pin"
        else -> ""
    }

    companion object {
        fun daysToString(days: Set<Int>): String = days.sorted().joinToString(",")
    }
}
