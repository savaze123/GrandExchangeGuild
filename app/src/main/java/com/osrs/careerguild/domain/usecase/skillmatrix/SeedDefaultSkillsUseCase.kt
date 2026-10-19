package com.osrs.careerguild.domain.usecase.skillmatrix

import com.osrs.careerguild.domain.model.Skill
import com.osrs.careerguild.domain.repository.SkillRepository

/**
 * Seeds LOCAL_VAULT with the user's preloaded skills from project_graph.json
 * on the very first app launch (when the skill table is empty).
 *
 * Preloaded skills are sourced from the user_profile_constraints in
 * project_graph.json and grouped into the three original categories.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 */
class SeedDefaultSkillsUseCase(
    private val skillRepository: SkillRepository,
) {
    suspend operator fun invoke(): Result<Unit> =
        skillRepository.seedDefaultSkills(DEFAULT_SKILLS)

    companion object {
        // Sourced directly from project_graph.json meta.user_profile_constraints.preloaded_skills
        val DEFAULT_SKILLS: List<Skill> = buildList {
            // AI & Agents
            addAll(listOf("Gemini API", "AI Agents", "LLM Integration", "Prompt Engineering", "RAG")
                .mapIndexed { i, name -> Skill(name = name, category = "AI & Agents",      isActive = true,  level = 70 + i * 3) })
            // Backend & Data
            addAll(listOf("Python", "SQL", "REST APIs", "Airflow", "Kafka", "Hadoop", "Spark", "NoSQL", "ETL")
                .mapIndexed { i, name -> Skill(name = name, category = "Backend & Data",    isActive = true,  level = 60 + i * 2) })
            // Automation & Web
            addAll(listOf("Playwright", "Selenium", "Asyncio", "Shopify Dawn/API Integration", "Git")
                .mapIndexed { i, name -> Skill(name = name, category = "Automation & Web",  isActive = true,  level = 55 + i * 4) })
        }
    }
}
