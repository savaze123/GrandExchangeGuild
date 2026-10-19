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

    override fun observeAll(): Flow<List<JobListing>> =
        jobDao.observeAll().map { list -> list.map(JobEntity::toDomain) }

    override fun search(query: String): Flow<List<JobListing>> =
        jobDao.search(query).map { list -> list.map(JobEntity::toDomain) }

    override suspend fun upsert(job: JobListing): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { jobDao.upsert(job.toEntity()) }
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
}

// ---------------------------------------------------------------------------
// Mapper extensions — data ↔ domain
// ---------------------------------------------------------------------------

private fun JobEntity.toDomain(): JobListing = JobListing(
    id            = id,
    title         = title,
    company       = company,
    atsUrl        = atsUrl,
    source        = source,
    score         = score,
    matchedSkills = if (matchedSkills.isBlank()) emptyList()
                    else matchedSkills.split("|"),
    isBlocked     = isBlocked,
    appliedAt     = appliedAt,
)

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
)
