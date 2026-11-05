package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reusable applicant answers store encrypted in SQLCipher Room DB.
 * Maps question pattern strings or keys to the candidate's verified answers.
 * Presets start completely empty and are only populated when explicitly set by the user.
 */
@Entity(tableName = "applicant_answers")
data class ApplicantAnswerEntity(
    @PrimaryKey
    @ColumnInfo(name = "question_pattern")
    val questionPattern: String,
    @ColumnInfo(name = "answer")
    val answer: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
