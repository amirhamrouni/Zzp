package com.zzp.btwtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val countryCode: String = "NL",
    val kvkNumber: String? = null,
    val vatNumber: String? = null,
    val iban: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val customerId: Long? = null,
    val customerName: String,
    val customerEmail: String? = null,
    val issueDateEpochDay: Long,
    val dueDateEpochDay: Long,
    val description: String,
    val netCents: Long,
    val vatRate: Int,
    val vatCents: Long,
    val grossCents: Long,
    val status: String = "DRAFT",
    val paidAtEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "receipt_inbox")
data class ReceiptInboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String? = null,
    val merchantName: String? = null,
    val totalCents: Long? = null,
    val vatCents: Long? = null,
    val kvkNumber: String? = null,
    val dateEpochDay: Long? = null,
    val rawText: String = "",
    val status: String = "TO_REVIEW",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CustomerDao {
    @Insert suspend fun insert(customer: CustomerEntity): Long
    @Update suspend fun update(customer: CustomerEntity)
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): CustomerEntity?
    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface InvoiceDao {
    @Insert suspend fun insert(invoice: InvoiceEntity): Long
    @Update suspend fun update(invoice: InvoiceEntity)
    @Query("SELECT * FROM invoices ORDER BY issueDateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>
    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): InvoiceEntity?
    @Query("UPDATE invoices SET status = :status, paidAtEpochDay = :paidAtEpochDay WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, paidAtEpochDay: Long? = null)
    @Query("SELECT COUNT(*) FROM invoices WHERE invoiceNumber LIKE :yearPrefix || '%'")
    suspend fun countForYear(yearPrefix: String): Int
    @Query("DELETE FROM invoices WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ReceiptInboxDao {
    @Insert suspend fun insert(item: ReceiptInboxEntity): Long
    @Update suspend fun update(item: ReceiptInboxEntity)
    @Query("SELECT * FROM receipt_inbox WHERE status = 'TO_REVIEW' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<ReceiptInboxEntity>>
    @Query("UPDATE receipt_inbox SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
    @Query("DELETE FROM receipt_inbox WHERE id = :id")
    suspend fun delete(id: Long)
}
