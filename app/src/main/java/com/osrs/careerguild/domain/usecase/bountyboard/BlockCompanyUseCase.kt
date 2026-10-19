package com.osrs.careerguild.domain.usecase.bountyboard

import com.osrs.careerguild.domain.repository.JobRepository

/**
 * Adds a company to the LOCAL_VAULT blocklist and marks all existing
 * job listings from that company as blocked.
 *
 * The TECH_RADAR_SCRAPER reads the blocklist on every scrape run so future
 * listings from this company are silently dropped before scoring.
 *
 * Node: LOCAL_VAULT (blocklist write)
 */
class BlockCompanyUseCase(
    private val jobRepository: JobRepository,
) {
    suspend operator fun invoke(company: String): Result<Unit> =
        jobRepository.blockCompany(company)
}
