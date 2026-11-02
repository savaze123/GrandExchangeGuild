package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an ATS portal account created and managed by the Courier Agent.
 * Stored inside the SQLCipher-encrypted LOCAL_VAULT (`career_guild.db`) with field-level
 * AES-GCM encryption (`passwordEncrypted`).
 */
@Entity(tableName = "courier_accounts")
data class CourierAccountEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "company_name")
    val companyName: String,

    @ColumnInfo(name = "job_title")
    val jobTitle: String,

    @ColumnInfo(name = "portal_url")
    val portalUrl: String,

    @ColumnInfo(name = "email_used")
    val emailUsed: String,

    @ColumnInfo(name = "password_encrypted")
    val passwordEncrypted: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
