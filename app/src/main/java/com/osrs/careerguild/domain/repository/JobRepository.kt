package com.osrs.careerguild.domain.repository

import com.osrs.careerguild.domain.model.JobListing
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the job listing data source (LOCAL_VAULT).
 *
 * Implementations use Room + SQLCipher + FTS5.
 * The TECH_RADAR_SCRAPER writes here after TRAJECTORY_SCORER grades listings.
 * [UI_3D_GUILD_HUB] reads from this to render tiered quest scrolls.
 *
 * Node: LOCAL_VAULT
 * Edges:
 *   ← TRAJECTORY_SCORER (saves_verified_high_growth_jobs)
 *   → UI_3D_GUILD_HUB   (renders_tiered_quest_scrolls_in_3d)
 */
interface JobRepository {

    /** All persisted jobs, ordered by trajectory score descending. */
    fun observeAll(): Flow<List<JobListing>>

    /** Full-text search via Room FTS5 across title, company, and skills. */
    fun search(query: String): Flow<List<JobListing>>

    /** Persist or update a scored job listing. */
    suspend fun upsert(job: JobListing): Result<Unit>

    /** Mark a job as applied with an ISO-8601 timestamp. */
    suspend fun markApplied(jobId: String, appliedAt: String): Result<Unit>

    /** Add a company to the LOCAL_VAULT blocklist. */
    suspend fun blockCompany(company: String): Result<Unit>

    /** Delete a job by id. */
    suspend fun delete(jobId: String): Result<Unit>

    /** Purge legacy TechRadar listings with generic portals. */
    suspend fun purgeLegacyListings(): Result<Unit>
}
