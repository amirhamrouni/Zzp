package com.zzp.btwtracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val description: String,
    val netCents: Long,
    val vatCents: Long,
    val grossCents: Long,
    val vatRate: Int,
    val vatTreatment: String,
    val taxBox: String,
    val dateEpochDay: Long,
    val counterpartyName: String? = null,
    val kvkNumber: String? = null,
    val vatNumber: String? = null,
    val countryCode: String = "NL",
    val receiptUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class QuarterTotals(val netCents: Long, val vatCents: Long, val grossCents: Long)

@Dao
interface TransactionDao {
    @Insert suspend fun insert(transaction: TransactionEntity): Long
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay ASC")
    suspend fun forPeriod(startEpochDay: Long, endEpochDay: Long): List<TransactionEntity>
    @Query("SELECT COALESCE(SUM(netCents),0) AS netCents, COALESCE(SUM(vatCents),0) AS vatCents, COALESCE(SUM(grossCents),0) AS grossCents FROM transactions WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun totalsForPeriod(startEpochDay: Long, endEpochDay: Long): QuarterTotals
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class ZzpDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    companion object {
        @Volatile private var instance: ZzpDatabase? = null
        fun get(context: Context): ZzpDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ZzpDatabase::class.java, "zzp_btw_tracker.db")
                .build().also { instance = it }
        }
    }
}
