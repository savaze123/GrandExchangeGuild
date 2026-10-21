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
    // GitHub-hosted Markdown & JSON feeds (free, no auth, updated regularly)
    // -----------------------------------------------------------------------

    data object SimplifyReadmeTable : JobFeedSource {
        override val label = "SimplifyJobs README (Live)"
        override val url   = "https://raw.githubusercontent.com/SimplifyJobs/New-Grad-Positions/dev/README.md"
    }

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

    data object PittCscNewGrad : JobFeedSource {
        override val label = "PittCSC Tech Listings"
        override val url   = "https://raw.githubusercontent.com/SimplifyJobs/" +
            "New-Grad-Positions/dev/.github/scripts/listings.json"
    }

    // -----------------------------------------------------------------------
    // Remotive & Arbeitnow Direct JSON APIs
    // -----------------------------------------------------------------------

    data object RemotiveSoftwareDev : JobFeedSource {
        override val label = "Remotive (Direct)"
        override val url   = "https://remotive.com/api/remote-jobs?category=software-dev"
    }

    data object ArbeitnowJobBoard : JobFeedSource {
        override val label = "Arbeitnow (Direct)"
        override val url   = "https://www.arbeitnow.com/api/job-board-api"
    }

    // -----------------------------------------------------------------------
    // LinkedIn Direct Job Feed (Direct Requisitions without tracking redirects)
    // -----------------------------------------------------------------------

    data class LinkedInJobs(
        val keywords: String = "Software Engineer",
        val location: String = "United States",
        val start: Int = 0,
    ) : JobFeedSource {
        companion object : JobFeedSource {
            override val label = "LinkedIn (Direct)"
            override val url   = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=Software%20Engineer&location=United%20States&start=0"
        }
        override val label = "LinkedIn (Direct)"
        override val url   = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=" +
            java.net.URLEncoder.encode(keywords, "UTF-8") +
            "&location=" +
            java.net.URLEncoder.encode(location, "UTF-8") +
            "&start=$start"
    }

    // -----------------------------------------------------------------------
    // Top-Tier Public ATS Endpoints (Middleman-free direct applications)
    // -----------------------------------------------------------------------

    data class GreenhouseBoard(val slug: String, val companyName: String) : JobFeedSource {
        override val label = "$companyName (Greenhouse)"
        override val url   = "https://boards-api.greenhouse.io/v1/boards/$slug/jobs"
    }

    data class LeverBoard(val slug: String, val companyName: String) : JobFeedSource {
        override val label = "$companyName (Lever)"
        override val url   = "https://api.lever.co/v0/postings/$slug?mode=json"
    }

    data class AshbyBoard(val slug: String, val companyName: String) : JobFeedSource {
        override val label = "$companyName (Ashby)"
        override val url   = "https://api.ashbyhq.com/posting-api/job-board/$slug"
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
