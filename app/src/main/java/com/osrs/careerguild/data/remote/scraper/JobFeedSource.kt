package com.osrs.careerguild.data.remote.scraper

/**
 * Sealed hierarchy of job feed sources consumed by [TechRadarScraperImpl].
 *
 * Each source maps to a concrete Ktor fetch strategy. Adding a new source
 * only requires a new subtype here + a handler in the scraper — no changes
 * to the domain layer.
 *
 * Node: TECH_RADAR_SCRAPER
 */
sealed interface JobFeedSource {

    /** Label shown in the UI and stored in [JobListing.source]. */
    val label: String

    /** Base URL fetched by Ktor. */
    val url: String

    // -----------------------------------------------------------------------
    // GitHub-hosted SimplifyJobs JSON feeds (free, no auth, updated daily)
    // -----------------------------------------------------------------------

    data object SimplifyNewGrad : JobFeedSource {
        override val label = "SimplifyJobs New Grad"
        override val url   = "https://raw.githubusercontent.com/SimplifyJobs/" +
            "New-Grad-Positions/dev/.github/scripts/listings.json"
    }

    data object SimplifyInternship : JobFeedSource {
        override val label = "SimplifyJobs Internship"
        override val url   = "https://raw.githubusercontent.com/SimplifyJobs/" +
            "Summer2026-Internships/dev/.github/scripts/listings.json"
    }

    // -----------------------------------------------------------------------
    // Stub sources — Phase 3 will implement real fetch + parse logic
    // -----------------------------------------------------------------------

    data object YCombinator : JobFeedSource {
        override val label = "YC Work at a Startup"
        override val url   = "https://www.workatastartup.com/jobs"
    }

    data object Wellfound : JobFeedSource {
        override val label = "Wellfound"
        override val url   = "https://wellfound.com/jobs"
    }
}
