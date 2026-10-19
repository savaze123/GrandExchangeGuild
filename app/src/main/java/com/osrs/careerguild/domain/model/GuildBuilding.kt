package com.osrs.careerguild.domain.model

/**
 * Represents the interactive buildings inside [UI_3D_GUILD_HUB].
 * Each building maps to a major subsystem in project_graph.json.
 *
 * Tapping a building in the 3D hub navigates the detail pane to the
 * corresponding feature screen via [UI_SPLIT_SCAFFOLD].
 */
enum class GuildBuilding(val displayName: String, val description: String) {

    /** TECH_RADAR_SCRAPER → TRAJECTORY_SCORER → LOCAL_VAULT */
    BOUNTY_BOARD(
        displayName = "Bounty Board",
        description = "Live job radar — scored quests from SimplifyJobs, YC, Wellfound & more.",
    ),

    /** RESUME_FORGE_PDF + DYNAMIC_SKILL_MATRIX */
    SCRIBE_TOWER(
        displayName = "Scribe Tower",
        description = "ATS resume & cover-letter forge. Output: <100 KB single-column PDF.",
    ),

    /** DYNAMIC_SKILL_MATRIX */
    SKILL_ALTAR(
        displayName = "Skill Altar",
        description = "OSRS-style skill tree. Add, remove, or toggle skills powering your search.",
    ),

    /** AUTO_APPLY_AGENT */
    COURIER_GUILD(
        displayName = "Courier Guild",
        description = "Auto-apply queue: Greenhouse, Lever & Ashby form autofill ledger.",
    ),
}
