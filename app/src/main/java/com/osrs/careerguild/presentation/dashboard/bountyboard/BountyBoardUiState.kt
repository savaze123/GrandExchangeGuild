package com.osrs.careerguild.presentation.dashboard.bountyboard

import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.model.Skill

/**
 * MVI UiState for the Bounty Board screen (TECH_RADAR_SCRAPER + TRAJECTORY_SCORER).
 *
 * Rendered by [BountyBoardScreen] and owned by [BountyBoardViewModel].
 */
data class BountyBoardUiState(

    /** Scored and persisted listings from LOCAL_VAULT, score-descending. */
    val jobs           : List<JobListing>   = emptyList(),

    /** True while the initial Room Flow is being collected. */
    val isLoading      : Boolean            = true,

    /** True while the Ktor scraper is fetching remote feeds. */
    val isScraping     : Boolean            = false,

    /** True while Gemini is scoring individual listings. */
    val isScoring      : Boolean            = false,

    /** Progress info shown while Gemini scores each listing. */
    val scoringProgress: ScoringProgress?   = null,

    /** Non-null when a recoverable error has occurred. */
    val error          : String?            = null,

    /** Current search text applied to the jobs list. */
    val searchQuery    : String             = "",

    /**
     * Search Parameters — Section 3:
     * Dynamic zip code with configurable search radius.
     */
    val zipCode        : String             = "",
    val searchRadius   : String             = "25 mi",
    val workMode       : String             = "All",

    /** Snackbar-style confirmation after blocking a company. */
    val blockedCompany : String?            = null,

    /**
     * Active skills from DYNAMIC_SKILL_MATRIX — used for job list filtering
     * and injected into scraper search queries on next refresh.
     */
    val activeSkills   : List<Skill>        = emptyList(),

    /**
     * False when TokenGovernor detects the $10/month Gemini budget is exhausted.
     * UI can surface a "Keyword-only mode" badge when false.
     */
    val isGeminiAllowed: Boolean            = true,
)

/**
 * Incremental progress emitted while TRAJECTORY_SCORER processes listings.
 *
 * @property current          Number of listings scored so far.
 * @property total            Total listings queued for scoring.
 * @property currentJobTitle  Title of the listing currently being scored.
 */
data class ScoringProgress(
    val current         : Int,
    val total           : Int,
    val currentJobTitle : String,
)
