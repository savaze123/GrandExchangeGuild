package com.osrs.careerguild.domain.scorer

import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.model.Skill

/**
 * Domain contract for the TRAJECTORY_SCORER node.
 *
 * Implementations score a raw job listing against the user's active skill set
 * and return an enriched copy with a 1–100 trajectory score.
 *
 * Primary impl: [GeminiTrajectoryScorer] (Gemini 1.5 Flash API with on-device
 * Gemini Nano / AICore fallback planned for Phase 3).
 *
 * Node: TRAJECTORY_SCORER
 * Edge ← TECH_RADAR_SCRAPER (pipes_raw_listings_for_5yr_growth_grading)
 * Edge → LOCAL_VAULT       (saves_verified_high_growth_jobs)
 */
interface TrajectoryScorer {

    /**
     * Score a single [job] on 5-year career growth potential.
     *
     * @param job          Raw listing from the scraper.
     * @param activeSkills Current active skills from DYNAMIC_SKILL_MATRIX.
     * @return             [ScoredJob] containing the enriched listing or a
     *                     [Result.failure] on API / parse error.
     */
    suspend fun score(job: JobListing, activeSkills: List<Skill>): Result<ScoredJob>
}

/**
 * Output of a single scoring pass.
 *
 * @property listing      Enriched [JobListing] with score and matchedSkills set.
 * @property isStaffing   True if the scorer detected a predatory staffing agency.
 * @property reasoning    Short explanation from the model (for debug / logs).
 */
data class ScoredJob(
    val listing    : JobListing,
    val isStaffing : Boolean,
    val reasoning  : String,
)
