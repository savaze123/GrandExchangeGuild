package com.osrs.careerguild.presentation.dashboard.skillaltar

import com.osrs.careerguild.domain.model.Skill

/**
 * MVI UiState for the Skill Altar screen (DYNAMIC_SKILL_MATRIX).
 *
 * Rendered by [SkillAltarScreen] and owned by [SkillAltarViewModel].
 */
data class SkillAltarUiState(

    /** All skills from LOCAL_VAULT, ordered by category then name. */
    val skills             : List<Skill>     = emptyList(),

    /** Loading flag while the initial Room Flow is being collected. */
    val isLoading          : Boolean         = true,

    /** Currently selected category tab — null means "All". */
    val selectedCategory   : String?         = null,

    /** Available categories derived from loaded skills. */
    val categories         : List<String>    = emptyList(),

    // --- Add-Skill form state ---
    val addSkillName       : String          = "",
    val addSkillCategory   : String          = "Custom",
    val isAddSkillExpanded : Boolean         = false,

    /** Non-null when an operation succeeded (shown as Snackbar). */
    val successMessage     : String?         = null,

    /** Non-null on recoverable errors. */
    val error              : String?         = null,
)

/** Filtered view of [SkillAltarUiState.skills] respecting [selectedCategory]. */
val SkillAltarUiState.filteredSkills: List<Skill>
    get() = if (selectedCategory == null) skills
            else skills.filter { it.category == selectedCategory }

/** Stat summary used in the Altar header. */
val SkillAltarUiState.activeCount: Int  get() = skills.count { it.isActive }
val SkillAltarUiState.totalCount : Int  get() = skills.size
