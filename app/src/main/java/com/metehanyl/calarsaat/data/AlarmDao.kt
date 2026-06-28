package com.metehanyl.calarsaat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE groupId IS NULL AND isAutoSabahNamazi = 0 ORDER BY hour, minute")
    fun observeUngrouped(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE groupId = :groupId ORDER BY hour, minute")
    suspend fun getByGroupId(groupId: Int): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): AlarmEntity?

    @Query("SELECT COUNT(*) FROM alarms")
    suspend fun count(): Int

    @Query("SELECT * FROM alarms WHERE isAutoSabahNamazi = 1")
    suspend fun getAutoSabahNamaziAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE isAutoSabahNamazi = 1 ORDER BY hour, minute")
    fun observeAutoSabahNamaziAlarms(): Flow<List<AlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Int)
}
