package com.zzp.btwtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val tradeName: String = "",
    val ownerName: String = "",
    val address: String = "",
    val postalCode: String = "",
    val city: String = "",
    val kvkNumber: String = "",
    val vatId: String = "",
    val iban: String = "",
    val email: String = "",
    val paymentTermDays: Int = 14
)

@Dao interface CompanyProfileDao {
    @Query("SELECT * FROM company_profile WHERE id = 1") fun observe(): Flow<CompanyProfileEntity?>
    @Upsert suspend fun save(profile: CompanyProfileEntity)
}
