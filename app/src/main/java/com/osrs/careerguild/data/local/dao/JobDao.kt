package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.JobEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for job listings (LOCAL_VAULT).
 *
 * All queries are parameterised — no string interpolation into SQL.
 * FTS5 full-text search is wired via a companion FTS table (Phase-2).
 *
 * Node: LOCAL_VAULT
 */
@Dao
interface JobDao {

    @Query("SELECT * FROM jobs WHERE is_blocked = 0 ORDER BY score DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("""
        SELECT * FROM jobs 
        WHERE is_blocked = 0 
          AND (title LIKE '%' || :query || '%' OR company LIKE '%' || :query || '%')
        ORDER BY score DESC
    """)
    fun search(query: String): Flow<List<JobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Query("UPDATE jobs SET applied_at = :appliedAt WHERE id = :jobId")
    suspend fun markApplied(jobId: String, appliedAt: String)

    @Query("UPDATE jobs SET is_blocked = 1 WHERE company = :company")
    suspend fun blockCompany(company: String)

    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun delete(jobId: String)
}
