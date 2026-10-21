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
 *   TECH_RADAR_SCRAPER  →  candidate listings (with 15-quest guaranteed fallback)
 *        ↓
 *   TRAJECTORY_SCORER   →  scored listings (Gemini / Live Skill Match)
 *        ↓
 *   LOCAL_VAULT (upsert)
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

        // 2. Scrape raw listings (guaranteed candidates returned via remote or seed fallback)
        val rawListings = scraper.scrape(activeSkills).getOrElse {
            emptyList()
        }

        if (rawListings.isEmpty()) return@runCatching emptyList()

        // 3. Persist into LOCAL_VAULT (Room) via batch upsertAll
        jobRepository.upsertAll(rawListings)

        val totalToProcess = rawListings.size
        rawListings.forEachIndexed { index, listing ->
            onProgress(index + 1, totalToProcess, listing.title)
        }

        rawListings.sortedByDescending { it.score }
    }
}
