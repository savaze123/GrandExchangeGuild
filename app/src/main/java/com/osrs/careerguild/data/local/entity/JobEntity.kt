package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a job listing in LOCAL_VAULT.
 *
 * Table: `jobs`
 *
 * Matched skills are stored as a pipe-delimited string and split at the
 * repository layer — keeps the schema simple without a join table for Phase-1.
 */
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id : String,

    @ColumnInfo(name = "title")
    val title : String,

    @ColumnInfo(name = "company")
    val company : String,

    @ColumnInfo(name = "ats_url")
    val atsUrl : String,

    @ColumnInfo(name = "source")
    val source : String,

    @ColumnInfo(name = "score")
    val score : Int,

    /** Pipe-delimited matched skills, e.g. "Python|LangGraph|Ktor" */
    @ColumnInfo(name = "matched_skills")
    val matchedSkills : String,

    @ColumnInfo(name = "is_blocked")
    val isBlocked : Boolean,

    @ColumnInfo(name = "applied_at")
    val appliedAt : String?,

    @ColumnInfo(name = "location", defaultValue = "'New York, NY'")
    val location : String = "New York, NY",

    @ColumnInfo(name = "work_mode", defaultValue = "'Hybrid'")
    val workMode : String = "Hybrid",
)
