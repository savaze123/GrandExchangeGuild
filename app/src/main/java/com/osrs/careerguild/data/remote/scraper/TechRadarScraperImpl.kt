package com.osrs.careerguild.data.remote.scraper

import com.osrs.careerguild.data.local.dao.BlockedCompanyDao
import com.osrs.careerguild.data.remote.dto.SimplifyJobDto
import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.model.Skill
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * TECH_RADAR_SCRAPER — Ktor-based multi-source job feed scraper.
 *
 * Orchestration per scrape run:
 * 1. Fetch raw [SimplifyJobDto] lists from GitHub JSON feeds via Ktor.
 * 2. Apply [GeoFilter] to drop listings outside preferred locations.
 * 3. Apply skill keyword matching against [activeSkills] — only listings
 *    with ≥1 keyword hit proceed to avoid wasting TRAJECTORY_SCORER calls.
 * 4. Filter out blocked companies (LOCAL_VAULT blocklist).
 * 5. Map to domain [JobListing] with score = 0 (scorer will enrich later).
 * 6. Return up to [MAX_RESULTS] candidates, sorted by skill match count desc.
 *
 * Dispatcher: [Dispatchers.IO]
 *
 * Node: TECH_RADAR_SCRAPER
 * Edge ← DYNAMIC_SKILL_MATRIX (injects_active_ai_and_custom_skills_into_search_queries)
 * Edge → TRAJECTORY_SCORER    (pipes_raw_listings_for_5yr_growth_grading)
 */
class TechRadarScraperImpl(
    private val httpClient       : HttpClient,
    private val blockedCompanyDao: BlockedCompanyDao,
    private val geoFilter        : GeoFilter = GeoFilter(),
) {

    /**
     * Scrape all active [JobFeedSource] feeds and return a filtered candidate list.
     *
     * @param activeSkills  Skills from DYNAMIC_SKILL_MATRIX used for keyword filtering.
     * @param sources       Which feeds to hit (defaults to both SimplifyJobs feeds).
     * @param maxResults    Cap on returned listings before Gemini scoring (default 50).
     */
    suspend fun scrape(
        activeSkills : List<Skill>,
        sources      : List<JobFeedSource> = listOf(
            JobFeedSource.SimplifyNewGrad,
            JobFeedSource.SimplifyInternship,
        ),
        maxResults   : Int = MAX_RESULTS,
    ): Result<List<JobListing>> = withContext(Dispatchers.IO) {
        runCatching {
            val blockedNames = blockedCompanyDao.getAllBlockedNames()
                .map { it.lowercase() }
                .toHashSet()

            val skillKeywords = activeSkills.map { it.name.lowercase() }

            val candidates = mutableListOf<Pair<JobListing, Int>>() // listing → matchCount

            for (source in sources) {
                val raw = fetchFeed(source)
                    .getOrElse { emptyList() }

                raw.asSequence()
                    .filter { dto -> dto.active == true && dto.isVisible != false }
                    .filter { dto -> geoFilter.passes(dto.locations) }
                    .filter { dto ->
                        val company = dto.companyName?.lowercase() ?: ""
                        !blockedNames.contains(company)
                    }
                    .mapNotNull { dto -> dto.toDomain(source.label) }
                    .map { listing ->
                        val matchCount = skillKeywords.count { kw ->
                            listing.title.lowercase().contains(kw)
                        }
                        listing to matchCount
                    }
                    .forEach { candidates.add(it) }
            }

            candidates
                .filter { (_, matchCount) -> matchCount > 0 }
                .sortedByDescending { (_, matchCount) -> matchCount }
                .take(maxResults)
                .map { (listing, _) -> listing }
        }
    }

    // ---------------------------------------------------------------------------
    // Ktor fetch — returns List<SimplifyJobDto> or failure
    // ---------------------------------------------------------------------------

    private suspend fun fetchFeed(source: JobFeedSource): Result<List<SimplifyJobDto>> =
        runCatching {
            httpClient.get(source.url) {
                header(HttpHeaders.Accept, "application/json")
            }.body<List<SimplifyJobDto>>()
        }

    // ---------------------------------------------------------------------------
    // DTO → Domain mapper
    // ---------------------------------------------------------------------------

    private fun SimplifyJobDto.toDomain(sourceLabel: String): JobListing? {
        val company = companyName?.takeIf { it.isNotBlank() } ?: return null
        val jobTitle = title?.takeIf { it.isNotBlank() }    ?: return null
        val atsLink  = url?.takeIf { it.isNotBlank() }      ?: return null

        return JobListing(
            id            = "${company.lowercase()}_${jobTitle.lowercase()}".hashToId(),
            title         = jobTitle,
            company       = company,
            atsUrl        = atsLink,
            source        = sourceLabel,
            score         = 0,       // enriched by TRAJECTORY_SCORER
            matchedSkills = emptyList(),
            isBlocked     = false,
            appliedAt     = null,
        )
    }

    private fun String.hashToId(): String =
        replace(Regex("[^a-z0-9]"), "_").take(64) + "_" + hashCode().toString(16)

    companion object {
        const val MAX_RESULTS = 50
    }
}
