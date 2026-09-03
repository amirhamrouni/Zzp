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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Database(
    entities = [
        TransactionEntity::class,
        CustomerEntity::class,
        InvoiceEntity::class,
        ReceiptInboxEntity::class,
        WorkSessionEntity::class,
        BusinessTripEntity::class,
        CompanyProfileEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ZzpDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun receiptInboxDao(): ReceiptInboxDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun businessTripDao(): BusinessTripDao
    abstract fun companyProfileDao(): CompanyProfileDao

    companion object {
        @Volatile private var instance: ZzpDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        email TEXT,
                        address TEXT,
                        postalCode TEXT,
                        city TEXT,
                        countryCode TEXT NOT NULL,
                        kvkNumber TEXT,
                        vatNumber TEXT,
                        iban TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS invoices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        invoiceNumber TEXT NOT NULL,
                        customerId INTEGER,
                        customerName TEXT NOT NULL,
                        customerEmail TEXT,
                        issueDateEpochDay INTEGER NOT NULL,
                        dueDateEpochDay INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        netCents INTEGER NOT NULL,
                        vatRate INTEGER NOT NULL,
                        vatCents INTEGER NOT NULL,
                        grossCents INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        paidAtEpochDay INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receipt_inbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        imageUri TEXT,
                        merchantName TEXT,
                        totalCents INTEGER,
                        vatCents INTEGER,
                        kvkNumber TEXT,
                        dateEpochDay INTEGER,
                        rawText TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS work_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateEpochDay INTEGER NOT NULL, minutes INTEGER NOT NULL, project TEXT NOT NULL, description TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS business_trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateEpochDay INTEGER NOT NULL, origin TEXT NOT NULL, destination TEXT NOT NULL, purpose TEXT NOT NULL, kilometersTimes10 INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS company_profile (id INTEGER NOT NULL PRIMARY KEY, tradeName TEXT NOT NULL, ownerName TEXT NOT NULL, address TEXT NOT NULL, postalCode TEXT NOT NULL, city TEXT NOT NULL, kvkNumber TEXT NOT NULL, vatId TEXT NOT NULL, iban TEXT NOT NULL, email TEXT NOT NULL, paymentTermDays INTEGER NOT NULL)")
            }
        }

        fun get(context: Context): ZzpDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ZzpDatabase::class.java,
                "zzp_btw_tracker.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
