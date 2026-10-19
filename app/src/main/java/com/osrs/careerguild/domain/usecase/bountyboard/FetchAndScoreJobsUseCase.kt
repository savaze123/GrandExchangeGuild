package com.osrs.careerguild.domain.usecase.bountyboard

import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.repository.JobRepository
import com.osrs.careerguild.domain.repository.SkillRepository
import com.osrs.careerguild.domain.scorer.GeminiTrajectoryScorer
import com.osrs.careerguild.domain.scorer.TrajectoryScorer
import com.osrs.careerguild.data.remote.scraper.TechRadarScraperImpl
import kotlinx.coroutines.flow.firstOrNull

/**
 * Orchestrates a full Bounty Board refresh cycle:
 *
 *   DYNAMIC_SKILL_MATRIX
 *        ↓ (active skills)
 *   TECH_RADAR_SCRAPER  →  raw listings
 *        ↓
 *   TRAJECTORY_SCORER   →  scored listings (Gemini 1.5 Flash)
 *        ↓
 *   LOCAL_VAULT (upsert)
 *
 * Progress is emitted via [onProgress] so the ViewModel can update the UI
 * with per-listing scoring feedback without coupling this use-case to Compose.
 *
 * @param onProgress  Callback: (current: Int, total: Int, jobTitle: String) → Unit
 */
class FetchAndScoreJobsUseCase(
    private val scraper        : TechRadarScraperImpl,
    private val scorer         : TrajectoryScorer,
    private val jobRepository  : JobRepository,
    private val skillRepository: SkillRepository,
) {

    suspend operator fun invoke(
        onProgress: (current: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
    ): Result<List<JobListing>> = runCatching {

        // 1. Get active skills from DYNAMIC_SKILL_MATRIX
        val activeSkills = skillRepository.observeActive().firstOrNull() ?: emptyList()

        // 2. Scrape raw listings with keyword pre-filter
        val rawListings = scraper.scrape(activeSkills).getOrElse {
            throw it  // propagate scraper errors
        }

        if (rawListings.isEmpty()) return@runCatching emptyList()

        // 3. Cap Gemini calls per session
        val toScore = rawListings.take(GeminiTrajectoryScorer.MAX_JOBS_PER_SESSION)

        // 4. Score each listing and persist only high-quality results
        val scored = mutableListOf<JobListing>()

        toScore.forEachIndexed { index, listing ->
            onProgress(index + 1, toScore.size, listing.title)

            val result = scorer.score(listing, activeSkills)
            result.onSuccess { scoredJob ->
                // Drop confirmed staffing agencies — never persist them
                if (!scoredJob.isStaffing) {
                    jobRepository.upsert(scoredJob.listing)
                    scored.add(scoredJob.listing)
                }
            }
            // On scoring failure: persist with score=0 rather than losing the listing
            result.onFailure {
                jobRepository.upsert(listing)
                scored.add(listing)
            }
        }

        scored.sortedByDescending { it.score }
    }
}
