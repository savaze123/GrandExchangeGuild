package com.osrs.careerguild.data.repository

import com.osrs.careerguild.data.local.dao.SkillDao
import com.osrs.careerguild.data.local.entity.SkillEntity
import com.osrs.careerguild.domain.model.Skill
import com.osrs.careerguild.domain.repository.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
            runCatching {
                val trimmedName = skill.name.trim()
                val existing = dao.observeAll().firstOrNull().orEmpty()
                val normTarget = normalizeSkillKey(trimmedName)
                val duplicate = existing.firstOrNull { normalizeSkillKey(it.name) == normTarget }
                if (duplicate == null) {
                    dao.insert(
                        skill.copy(
                            name = trimmedName,
                            category = skill.category.trim().ifBlank { inferCategory(trimmedName) },
                        ).toEntity()
                    )
                }
            }
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
            runCatching {
                val existing = dao.observeAll().firstOrNull().orEmpty()
                val existingKeys = existing.map { normalizeSkillKey(it.name) }.toMutableSet()
                val toInsert = skills.filter { s ->
                    val key = normalizeSkillKey(s.name)
                    key.isNotBlank() && existingKeys.add(key)
                }
                if (toInsert.isNotEmpty()) {
                    dao.insertAll(toInsert.map(Skill::toEntity))
                }
            }
        }

    override suspend fun mergeResumeSkills(rawSkills: Collection<String>): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existingEntities = dao.observeAll().firstOrNull().orEmpty()
                val existingSkillsSet: Set<Skill> = existingEntities.map(SkillEntity::toDomain).toSet()
                val existingNormalizedKeys = existingSkillsSet
                    .map { normalizeSkillKey(it.name) }
                    .toMutableSet()

                val newSkillsUnion = linkedSetOf<Skill>()
                for (raw in rawSkills) {
                    val cleaned = raw.trim().trimStart('•', '-', '*').trim().trimEnd('.', ',', ';')
                    if (cleaned.length < 2 || cleaned.length > 42) continue
                    val normKey = normalizeSkillKey(cleaned)
                    if (normKey.isBlank() || normKey in existingNormalizedKeys) continue

                    existingNormalizedKeys.add(normKey)
                    val canonicalName = canonicalSkillDisplayName(cleaned, normKey)
                    val category = inferCategory(canonicalName)
                    newSkillsUnion.add(
                        Skill(
                            name = canonicalName,
                            category = category,
                            isActive = true,
                            level = 75,
                        )
                    )
                }

                if (newSkillsUnion.isNotEmpty()) {
                    dao.insertAll(newSkillsUnion.map(Skill::toEntity))
                }
                newSkillsUnion.size
            }
        }

    companion object {
        private val aliasMap = mapOf(
            "js" to "javascript",
            "javascript (es6+)" to "javascript",
            "es6" to "javascript",
            "ts" to "typescript",
            "py" to "python",
            "python 3" to "python",
            "postgres" to "postgresql",
            "postgre" to "postgresql",
            "ms sql" to "sql server",
            "mssql" to "sql server",
            "mongo" to "mongodb",
            "k8s" to "kubernetes",
            "llm" to "llm integration",
            "llms" to "llm integration",
            "llm-assisted debugging and code" to "llm integration",
            "ai agent" to "ai agents",
            "agentic workflows" to "ai agents",
            "ai-assisted workflows" to "ai agents",
            "git/github" to "git",
            "github" to "git",
            "ci/cd principles" to "ci/cd",
            "continuous integration" to "ci/cd",
            "linux" to "linux/unix",
            "unix" to "linux/unix",
            "restful apis" to "rest apis",
            "rest api" to "rest apis",
        )

        fun normalizeSkillKey(raw: String): String {
            val base = raw.lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.', ',', ';')
            return aliasMap[base] ?: base
        }

        fun canonicalSkillDisplayName(cleaned: String, normKey: String): String {
            return when (normKey) {
                "javascript" -> "JavaScript"
                "typescript" -> "TypeScript"
                "python" -> "Python"
                "postgresql" -> "PostgreSQL"
                "mysql" -> "MySQL"
                "sql server" -> "SQL Server"
                "mongodb" -> "MongoDB"
                "nosql" -> "NoSQL"
                "sql" -> "SQL"
                "llm integration" -> "LLM Integration"
                "ai agents" -> "AI Agents"
                "rag" -> "RAG"
                "git" -> "Git"
                "ci/cd" -> "CI/CD"
                "linux/unix" -> "Linux/Unix"
                "rest apis" -> "REST APIs"
                else -> cleaned
            }
        }

        fun inferCategory(skillName: String): String {
            val lower = skillName.lowercase()
            return when {
                listOf("ai", "llm", "rag", "prompt", "gemini", "agent", "copilot", "cursor", "openai", "ml", "langchain", "vector").any { lower.contains(it) } ->
                    "AI & Agents"
                listOf("playwright", "selenium", "javascript", "typescript", "react", "html", "css", "git", "docker", "linux", "unix", "ci/cd", "agile", "asyncio", "web", "rest", "api").any { lower.contains(it) } ->
                    "Automation & Web"
                else ->
                    "Backend & Data"
            }
        }
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
