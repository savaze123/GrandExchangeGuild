package com.osrs.careerguild.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single skill entry in the [DYNAMIC_SKILL_MATRIX].
 *
 * Skills are stored in Room via [LOCAL_VAULT], displayed on the Skill Altar
 * in [UI_3D_GUILD_HUB], and consumed by [TECH_RADAR_SCRAPER] for search
 * query injection and by [RESUME_FORGE_PDF] for ATS keyword tailoring.
 *
 * @property name     Display name (e.g. "LangGraph", "PyTorch").
 * @property category Broad category for OSRS-style grouping (e.g. "AI", "Backend").
 * @property isActive Whether this skill is currently driving scraper queries.
 * @property level    OSRS-style level 1–99 representing proficiency (optional / cosmetic).
 */
@Serializable
data class Skill(
    val name     : String,
    val category : String,
    val isActive : Boolean = true,
    val level    : Int     = 1,
)
