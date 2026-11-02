package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.CourierAccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for 'Accounts Made by Courier' (`courier_accounts` table).
 */
@Dao
interface CourierAccountDao {

    @Query("SELECT * FROM courier_accounts ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CourierAccountEntity>>

    @Query("SELECT * FROM courier_accounts ORDER BY created_at DESC")
    suspend fun getAll(): List<CourierAccountEntity>

    @Query(
        "SELECT * FROM courier_accounts " +
            "WHERE LOWER(portal_url) = LOWER(:portalUrl) " +
            "OR LOWER(company_name) = LOWER(:companyName) " +
            "ORDER BY created_at DESC LIMIT 1"
    )
    suspend fun findByPortalOrCompany(portalUrl: String, companyName: String): CourierAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: CourierAccountEntity): Long

    @Query("DELETE FROM courier_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
