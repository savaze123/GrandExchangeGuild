package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent background Courier task entity stored in SQLCipher Room DB (`courier_task`).
 * Checkpointed after every step so WorkManager process death or app kills can resume cleanly.
 */
@Entity(tableName = "courier_task")
data class CourierTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "job_id", defaultValue = "''")
    val jobId: String,

    @ColumnInfo(name = "job_url", defaultValue = "''")
    val jobUrl: String,

    @ColumnInfo(name = "ats", defaultValue = "''")
    val ats: String,

    @ColumnInfo(name = "title", defaultValue = "''")
    val title: String = "",

    @ColumnInfo(name = "company", defaultValue = "''")
    val company: String = "",

    @ColumnInfo(name = "status", defaultValue = "'QUEUED'")
    val status: String = "QUEUED",

    @ColumnInfo(name = "step", defaultValue = "'INIT'")
    val step: String = "INIT",

    @ColumnInfo(name = "current_url", defaultValue = "''")
    val currentUrl: String = "",

    @ColumnInfo(name = "filled_fields_json", defaultValue = "'{}'")
    val filledFieldsJson: String = "{}",

    @ColumnInfo(name = "blocked_reason")
    val blockedReason: String? = null,

    @ColumnInfo(name = "blocked_fields_json", defaultValue = "'[]'")
    val blockedFieldsJson: String = "[]",

    @ColumnInfo(name = "resume_pdf_path")
    val resumePdfPath: String? = null,

    @ColumnInfo(name = "submit_policy", defaultValue = "'REVIEW_BEFORE_SUBMIT'")
    val submitPolicy: String = "REVIEW_BEFORE_SUBMIT",

    @ColumnInfo(name = "review_required", defaultValue = "0")
    val reviewRequired: Boolean = false,

    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "snoozed_until")
    val snoozedUntil: Long? = null,

    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)
