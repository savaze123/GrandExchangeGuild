package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.ApplicantAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicantAnswerDao {
    @Query("SELECT * FROM applicant_answers ORDER BY question_pattern ASC")
    fun observeAll(): Flow<List<ApplicantAnswerEntity>>

    @Query("SELECT * FROM applicant_answers ORDER BY question_pattern ASC")
    suspend fun getAll(): List<ApplicantAnswerEntity>

    @Query("SELECT * FROM applicant_answers WHERE question_pattern = :pattern LIMIT 1")
    suspend fun getByPattern(pattern: String): ApplicantAnswerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ApplicantAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<ApplicantAnswerEntity>)

    @Query("DELETE FROM applicant_answers WHERE question_pattern = :pattern")
    suspend fun deleteByPattern(pattern: String)

    @Query("DELETE FROM applicant_answers")
    suspend fun clear()
}
