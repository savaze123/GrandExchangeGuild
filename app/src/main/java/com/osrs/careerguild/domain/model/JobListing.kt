package com.osrs.careerguild.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single job listing surfaced by [TECH_RADAR_SCRAPER] and scored
 * by [TRAJECTORY_SCORER] before being persisted in [LOCAL_VAULT].
 *
 * @property id          Stable unique identifier (URL-hash or feed id).
 * @property title       Job title as scraped from the source.
 * @property company     Hiring company name.
 * @property atsUrl      Live ATS application URL (Greenhouse / Lever / Ashby).
 * @property source      Feed origin (e.g. "PittCSC", "YC W25", "Wellfound").
 * @property score       Trajectory score 1–100 assigned by TRAJECTORY_SCORER.
 * @property matchedSkills Skills from DYNAMIC_SKILL_MATRIX matched to the JD.
 * @property isBlocked   True if the company is on the LOCAL_VAULT blocklist.
 * @property appliedAt   ISO-8601 timestamp if AUTO_APPLY_AGENT submitted the form.
 */
@Serializable
data class JobListing(
    val id            : String,
    val title         : String,
    val company       : String,
    val atsUrl        : String,
    val source        : String,
    val score         : Int            = 0,
    val matchedSkills : List<String>   = emptyList(),
    val isBlocked     : Boolean        = false,
    val appliedAt     : String?        = null,
    val description   : String         = "",
    val location      : String         = "New York, NY",
    val workMode      : String         = "Hybrid",
)
