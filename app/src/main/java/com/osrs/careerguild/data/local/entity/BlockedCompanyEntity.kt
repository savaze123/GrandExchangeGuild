package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the company blocklist stored in LOCAL_VAULT.
 *
 * Table: `blocked_companies`
 *
 * Populated by [AUTO_APPLY_AGENT] or manually by the user via the Bounty Board.
 * The [TECH_RADAR_SCRAPER] checks this table before persisting new listings,
 * and all DAO queries on `jobs` filter by [JobEntity.isBlocked].
 */
@Entity(tableName = "blocked_companies")
data class BlockedCompanyEntity(
    @PrimaryKey
    @ColumnInfo(name = "company_name")
    val companyName: String,

    @ColumnInfo(name = "blocked_at")
    val blockedAt: String,  // ISO-8601 timestamp
)
