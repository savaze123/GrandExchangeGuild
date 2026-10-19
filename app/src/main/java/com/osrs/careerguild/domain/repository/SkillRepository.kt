package com.osrs.careerguild.domain.repository

import com.osrs.careerguild.domain.model.Skill
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the DYNAMIC_SKILL_MATRIX data source.
 *
 * Implementations live in the data layer (Room DAO + SQLCipher via LOCAL_VAULT).
 * This interface is the domain boundary — the presentation layer and
 * SAFE_CLI_ENGINE dispatcher reference only this contract.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 * Edges:
 *   → TECH_RADAR_SCRAPER  (active skills injected into search queries)
 *   → RESUME_FORGE_PDF    (active skills used for ATS tailoring)
 */
interface SkillRepository {

    /** Reactive stream of all skills. Updates on every Room write. */
    fun observeAll(): Flow<List<Skill>>

    /** Active skills only — consumed by scraper and PDF forge. */
    fun observeActive(): Flow<List<Skill>>

    /** Insert or replace a skill. Returns [Result.success] or [Result.failure]. */
    suspend fun addSkill(skill: Skill): Result<Unit>

    /** Toggle the [Skill.isActive] flag for the skill with [name]. */
    suspend fun toggleSkill(name: String): Result<Unit>

    /** Permanently delete a skill by [name]. */
    suspend fun removeSkill(name: String): Result<Unit>

    /** Seed the vault with the user's preloaded skills from project_graph.json meta. */
    suspend fun seedDefaultSkills(skills: List<Skill>): Result<Unit>
}
