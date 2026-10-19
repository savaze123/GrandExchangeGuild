package com.osrs.careerguild.domain.usecase.skillmatrix

import com.osrs.careerguild.domain.repository.SkillRepository

/**
 * Flips the [Skill.isActive] flag for a named skill in DYNAMIC_SKILL_MATRIX.
 *
 * Active skills drive [TechRadarScraperImpl] query construction and
 * [ResumeForge] keyword injection.
 *
 * Called by:
 *  - [SkillAltarViewModel] from the toggle switch in each [SkillCard].
 *  - [CliDispatcher] when `/skill toggle <name>` is parsed.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 * Edge ← SAFE_CLI_ENGINE (adds_or_toggles_skills_via_safe_commands)
 */
class ToggleSkillUseCase(
    private val skillRepository: SkillRepository,
) {
    suspend operator fun invoke(name: String): Result<Unit> =
        skillRepository.toggleSkill(name)
}
