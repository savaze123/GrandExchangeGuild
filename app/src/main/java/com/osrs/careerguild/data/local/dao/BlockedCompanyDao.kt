package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.BlockedCompanyEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the company blocklist (LOCAL_VAULT).
 *
 * Consumed by [TechRadarScraperImpl] before persisting new listings,
 * and by the Bounty Board UI for displaying blocked state.
 *
 * All queries are parameterised — no string interpolation into SQL.
 */
@Dao
interface BlockedCompanyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun block(company: BlockedCompanyEntity)

    @Query("SELECT company_name FROM blocked_companies")
    suspend fun getAllBlockedNames(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_companies WHERE company_name = :name LIMIT 1)")
    suspend fun isBlocked(name: String): Boolean

    @Query("DELETE FROM blocked_companies WHERE company_name = :name")
    suspend fun unblock(name: String)

    @Query("SELECT * FROM blocked_companies ORDER BY blocked_at DESC")
    fun observeAll(): Flow<List<BlockedCompanyEntity>>
}
