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
    val textPassHash: String? = null,
    val lockSteps: String = "",
    val intervalGroupId: Int = -1,
    val isSnoozed: Boolean = false,
    val snoozedUntilMillis: Long = 0L
) {
    fun repeatDaysSet(): Set<Int> =
        if (repeatDays.isBlank()) emptySet()
        else repeatDays.split(",").map { it.trim().toInt() }.toSet()

    fun isRepeating(): Boolean = repeatDays.isNotBlank()

    fun effectiveLockType(): String = effectiveLockSteps().firstOrNull() ?: ""

    fun effectiveLockSteps(): List<String> {
        if (lockSteps.isNotEmpty()) return lockSteps.split(",").filter { it.isNotEmpty() }
        if (lockType.isNotEmpty()) return listOf(lockType)
        if (requirePin) return listOf("pin")
        return emptyList()
    }

    companion object {
        fun daysToString(days: Set<Int>): String = days.sorted().joinToString(",")
    }
}
