package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encrypted Room entity for storing the user's uploaded master base resume in LOCAL_VAULT.
 * Ground truth for all dynamic ATS tailoring, preview rendering, and PDF generation.
 */
@Entity(tableName = "base_resumes")
data class BaseResumeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "primary",

    @ColumnInfo(name = "raw_text")
    val rawText: String,

    @ColumnInfo(name = "confirmed_json")
    val confirmedJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
