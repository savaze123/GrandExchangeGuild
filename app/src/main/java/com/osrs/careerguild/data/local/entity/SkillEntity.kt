package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for DYNAMIC_SKILL_MATRIX stored in LOCAL_VAULT (SQLCipher).
 *
 * Table: `skills`
 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name     : String,

    @ColumnInfo(name = "category")
    val category : String,

    @ColumnInfo(name = "is_active")
    val isActive : Boolean,

    @ColumnInfo(name = "level")
    val level    : Int,
)
