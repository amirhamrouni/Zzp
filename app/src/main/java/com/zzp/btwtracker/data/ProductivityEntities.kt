package com.zzp.btwtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val minutes: Int,
    val project: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "business_trips")
data class BusinessTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val origin: String,
    val destination: String,
    val purpose: String,
    val kilometersTimes10: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface WorkSessionDao {
    @Insert suspend fun insert(item: WorkSessionEntity): Long
    @Query("SELECT * FROM work_sessions ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<WorkSessionEntity>>
    @Query("DELETE FROM work_sessions WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface BusinessTripDao {
    @Insert suspend fun insert(item: BusinessTripEntity): Long
    @Query("SELECT * FROM business_trips ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<BusinessTripEntity>>
    @Query("DELETE FROM business_trips WHERE id = :id") suspend fun delete(id: Long)
}
