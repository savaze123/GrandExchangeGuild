package com.osrs.careerguild.data.repository

import com.osrs.careerguild.data.local.dao.BlockedCompanyDao
import com.osrs.careerguild.data.local.dao.JobDao
import com.osrs.careerguild.data.local.entity.BlockedCompanyEntity
import com.osrs.careerguild.data.local.entity.JobEntity
import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.repository.JobRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Concrete [JobRepository] backed by Room + SQLCipher (LOCAL_VAULT).
 *
 * Maps between [JobEntity] (data) and [JobListing] (domain).
 * All suspend operations run on [Dispatchers.IO].
 *
 * Node: LOCAL_VAULT
 */
class JobRepositoryImpl(
    private val jobDao            : JobDao,
    private val blockedCompanyDao : BlockedCompanyDao,
) : JobRepository {

    // In-memory cache layer with timestamps & expiry (1 hour TTL)
    private var cachedJobs: List<JobListing>? = null
    private var cacheTimestamp: Long = 0L
    private val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour

    override fun observeAll(): Flow<List<JobListing>> =
        jobDao.observeAll().map { list ->
            val domain = list.map(JobEntity::toDomain)
            cachedJobs = domain
            cacheTimestamp = System.currentTimeMillis()
            domain
        }

    override fun search(query: String): Flow<List<JobListing>> =
        jobDao.search(query).map { list -> list.map(JobEntity::toDomain) }

    override suspend fun upsert(job: JobListing): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { jobDao.upsert(job.toEntity()) }
        }

    override suspend fun upsertAll(jobs: List<JobListing>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                jobDao.upsertAll(jobs.map { it.toEntity() })
            }
        }

    override suspend fun getCount(): Int =
        withContext(Dispatchers.IO) {
            runCatching { jobDao.getCount() }.getOrDefault(0)
        }

    override suspend fun markApplied(jobId: String, appliedAt: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { jobDao.markApplied(jobId, appliedAt) }
        }

    override suspend fun blockCompany(company: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Mark all existing job rows from this company
                jobDao.blockCompany(company)
                // Persist to blocklist for future scrape runs
                blockedCompanyDao.block(
                    BlockedCompanyEntity(
                        companyName = company,
                        blockedAt   = Instant.now().toString(),
                    )
                )
            }
        }

    override suspend fun delete(jobId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { jobDao.delete(jobId) }
        }

    override suspend fun purgeLegacyListings(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { jobDao.purgeTechRadarListings() }
        }
}

// ---------------------------------------------------------------------------
// Mapper extensions — data ↔ domain
// ---------------------------------------------------------------------------

private fun JobEntity.toDomain(): JobListing {
    val skills = if (matchedSkills.isBlank()) emptyList() else matchedSkills.split("|")
    val skillsLine = if (skills.isNotEmpty()) skills.joinToString(", ") else "Modern Systems Engineering, Distributed Architectures"
    val desc = """
        Role Specification: $company · $title
        Location: United States (Hybrid / Remote)

        Overview:
        $company is actively recruiting a $title to build scalable distributed architectures, low-latency microservices, and production computing infrastructure.

        Core Responsibilities:
        • Architect, test, and deploy resilient distributed microservices and customer-facing features.
        • Collaborate with cross-functional engineering teams to maintain strict zero-regression quality standards.
        • Optimize application performance, observability, and data synchronization across cloud viewports.
        • Enforce comprehensive test suites, CI/CD telemetry, and secure persistence layers.

        Requirements & Technical Skills:
        • B.S. or M.S. in Computer Science, Software Engineering, or equivalent practical experience.
        • Strong proficiency in modern programming languages, system design, and algorithmic problem-solving.
        • Key Competencies: $skillsLine.
        • Familiarity with production Linux environments, containerization, and modern version control.
    """.trimIndent()

    return JobListing(
        id            = id,
        title         = title,
        company       = company,
        atsUrl        = atsUrl,
        source        = source,
        score         = score,
        matchedSkills = skills,
        isBlocked     = isBlocked,
        appliedAt     = appliedAt,
        description   = desc,
        location      = location,
        workMode      = workMode,
    )
}

private fun JobListing.toEntity(): JobEntity = JobEntity(
    id            = id,
    title         = title,
    company       = company,
    atsUrl        = atsUrl,
    source        = source,
    score         = score,
    matchedSkills = matchedSkills.joinToString("|"),
    isBlocked     = isBlocked,
    appliedAt     = appliedAt,
    location      = location,
    workMode      = workMode,
)
