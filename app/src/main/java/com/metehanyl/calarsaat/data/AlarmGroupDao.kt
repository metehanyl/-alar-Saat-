package com.metehanyl.calarsaat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmGroupDao {

    @Query("SELECT * FROM alarm_groups ORDER BY id")
    fun observeAll(): Flow<List<AlarmGroupEntity>>

    @Query("SELECT * FROM alarm_groups WHERE id = :id")
    suspend fun getById(id: Int): AlarmGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: AlarmGroupEntity): Long

    @Update
    suspend fun update(group: AlarmGroupEntity)

    @Delete
    suspend fun delete(group: AlarmGroupEntity)
}
