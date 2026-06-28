package com.metehanyl.calarsaat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_groups")
data class AlarmGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)
