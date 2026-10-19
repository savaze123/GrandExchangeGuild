package com.osrs.careerguild.data.repository

import com.osrs.careerguild.data.local.dao.SkillDao
import com.osrs.careerguild.data.local.entity.SkillEntity
import com.osrs.careerguild.domain.model.Skill
import com.osrs.careerguild.domain.repository.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [SkillRepository] backed by Room + SQLCipher.
 *
 * Maps between [SkillEntity] (data layer) and [Skill] (domain layer).
 * All suspend operations run on [Dispatchers.IO].
 *
 * Node: LOCAL_VAULT ↔ DYNAMIC_SKILL_MATRIX
 */
class SkillRepositoryImpl(
    private val dao: SkillDao,
) : SkillRepository {

    override fun observeAll(): Flow<List<Skill>> =
        dao.observeAll().map { list -> list.map(SkillEntity::toDomain) }

    override fun observeActive(): Flow<List<Skill>> =
        dao.observeActive().map { list -> list.map(SkillEntity::toDomain) }

    override suspend fun addSkill(skill: Skill): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { dao.insert(skill.toEntity()) }
        }

    override suspend fun toggleSkill(name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { dao.toggleActive(name) }
        }

    override suspend fun removeSkill(name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { dao.delete(name) }
        }

    override suspend fun seedDefaultSkills(skills: List<Skill>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { dao.insertAll(skills.map(Skill::toEntity)) }
        }
}

// ---------------------------------------------------------------------------
// Mapper extensions (data ↔ domain)
// ---------------------------------------------------------------------------

private fun SkillEntity.toDomain(): Skill = Skill(
    name     = name,
    category = category,
    isActive = isActive,
    level    = level,
)

private fun Skill.toEntity(): SkillEntity = SkillEntity(
    name     = name,
    category = category,
    isActive = isActive,
    level    = level,
)
