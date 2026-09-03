package com.zzp.btwtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val category: String,
    val year: Int,
    val quarter: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao interface DocumentDao {
    @Insert suspend fun insert(item: DocumentEntity): Long
    @Query("SELECT * FROM documents ORDER BY year DESC, quarter DESC, createdAt DESC") fun observeAll(): Flow<List<DocumentEntity>>
    @Query("DELETE FROM documents WHERE id=:id") suspend fun delete(id: Long)
}
