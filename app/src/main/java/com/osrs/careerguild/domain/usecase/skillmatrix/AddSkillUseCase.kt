package com.osrs.careerguild.domain.usecase.skillmatrix

import com.osrs.careerguild.domain.model.Skill
import com.osrs.careerguild.domain.repository.SkillRepository

/**
 * Adds a new skill to DYNAMIC_SKILL_MATRIX (LOCAL_VAULT).
 *
 * Called by:
 *  - [SkillAltarViewModel] when the user taps "Add" in the Skill Altar UI.
 *  - [CliDispatcher] when `/skill add <name> [category]` is parsed.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 * Edge ← SAFE_CLI_ENGINE (adds_or_toggles_skills_via_safe_commands)
 */
class AddSkillUseCase(
    private val skillRepository: SkillRepository,
) {
    suspend operator fun invoke(name: String, category: String = "Custom"): Result<Unit> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return Result.failure(IllegalArgumentException("Skill name cannot be blank"))
        if (trimmedName.length > 64)  return Result.failure(IllegalArgumentException("Skill name too long (max 64 chars)"))

        return skillRepository.addSkill(
            Skill(
                name     = trimmedName,
                category = category.trim().ifBlank { "Custom" },
                isActive = true,
                level    = 1,
            )
        )
    }
}
