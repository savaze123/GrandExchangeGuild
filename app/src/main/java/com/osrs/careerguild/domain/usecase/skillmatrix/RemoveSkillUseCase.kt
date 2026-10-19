package com.osrs.careerguild.domain.usecase.skillmatrix

import com.osrs.careerguild.domain.repository.SkillRepository

/**
 * Permanently removes a skill from DYNAMIC_SKILL_MATRIX (LOCAL_VAULT).
 *
 * Called by:
 *  - [SkillAltarViewModel] on long-press delete in [SkillCard].
 *  - [CliDispatcher] when `/skill remove <name>` is parsed.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 */
class RemoveSkillUseCase(
    private val skillRepository: SkillRepository,
) {
    suspend operator fun invoke(name: String): Result<Unit> =
        skillRepository.removeSkill(name)
}
