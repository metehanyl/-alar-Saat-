package com.metehanyl.calarsaat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IntervalAlarmGroupDao {

    @Query("SELECT * FROM interval_alarm_groups ORDER BY id")
    fun observeAll(): Flow<List<IntervalAlarmGroupEntity>>

    @Query("SELECT * FROM interval_alarm_groups WHERE id = :id")
    suspend fun getById(id: Int): IntervalAlarmGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: IntervalAlarmGroupEntity): Long

    @Update
    suspend fun update(group: IntervalAlarmGroupEntity)

    @Delete
    suspend fun delete(group: IntervalAlarmGroupEntity)
}
