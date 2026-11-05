package com.osrs.careerguild.core.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [SkillCategorizer] deterministic taxonomy and alias normalization.
 * Uses 100% synthetic skills fixtures per GEMINI.md hard invariant.
 */
class SkillCategorizerTest {

    @Test
    fun taxonomyCategorization_placesSkillsInCorrectCategories() {
        val syntheticSkills = listOf(
            "Python",
            "React",
            "PostgreSQL",
            "Docker",
            "CI/CD",
            "Prompt Engineering",
            "CustomInternalTooling",
        )

        val categorized = SkillCategorizer.categorize(syntheticSkills)

        assertEquals(listOf("Python"), categorized[SkillCategorizer.CAT_LANGUAGES])
        assertEquals(listOf("React"), categorized[SkillCategorizer.CAT_FRAMEWORKS])
        assertEquals(listOf("Docker", "PostgreSQL"), categorized[SkillCategorizer.CAT_DATABASES_TOOLS]?.sorted())
        assertTrue(categorized[SkillCategorizer.CAT_AI_DEV_TOOLING]?.contains("CI/CD") == true)
        assertTrue(categorized[SkillCategorizer.CAT_AI_DEV_TOOLING]?.contains("Prompt Engineering") == true)
        assertEquals(listOf("CustomInternalTooling"), categorized[SkillCategorizer.CAT_OTHER])
    }

    @Test
    fun aliases_normalizeCanonicalNamesCaseInsensitively() {
        val aliasSkills = listOf("postgres", "JS", "ts", "bash", "golang", "k8s")

        val categorized = SkillCategorizer.categorize(aliasSkills)

        val languages = categorized[SkillCategorizer.CAT_LANGUAGES].orEmpty()
        assertTrue(languages.contains("JavaScript"))
        assertTrue(languages.contains("TypeScript"))
        assertTrue(languages.contains("Bash/Shell"))
        assertTrue(languages.contains("Go"))

        val dbTools = categorized[SkillCategorizer.CAT_DATABASES_TOOLS].orEmpty()
        assertTrue(dbTools.contains("PostgreSQL"))
        assertTrue(dbTools.contains("Kubernetes"))
    }

    @Test
    fun aiAndDeveloperTooling_containsSpecifiedTooling() {
        val toolingSkills = listOf(
            "Prompt Engineering",
            "AI Coding Assistants",
            "CI/CD",
            "Playwright",
            "Gemini API",
        )

        val categorized = SkillCategorizer.categorize(toolingSkills)
        val aiDevCategory = categorized[SkillCategorizer.CAT_AI_DEV_TOOLING].orEmpty()

        assertEquals(5, aiDevCategory.size)
        assertTrue(aiDevCategory.contains("Prompt Engineering"))
        assertTrue(aiDevCategory.contains("AI Coding Assistants"))
        assertTrue(aiDevCategory.contains("CI/CD"))
        assertTrue(aiDevCategory.contains("Playwright"))
        assertTrue(aiDevCategory.contains("Gemini API"))
    }

    @Test
    fun emptyCategories_areSkipped() {
        val onlyLanguages = listOf("Kotlin", "Rust")

        val categorized = SkillCategorizer.categorize(onlyLanguages)

        assertTrue(categorized.containsKey(SkillCategorizer.CAT_LANGUAGES))
        assertFalse(categorized.containsKey(SkillCategorizer.CAT_FRAMEWORKS))
        assertFalse(categorized.containsKey(SkillCategorizer.CAT_DATABASES_TOOLS))
        assertFalse(categorized.containsKey(SkillCategorizer.CAT_AI_DEV_TOOLING))
        assertFalse(categorized.containsKey(SkillCategorizer.CAT_OTHER))
    }

    @Test
    fun jdKeywords_orderSkillsByRelevanceWithinCategory() {
        val languages = listOf("Go", "Python", "Kotlin")
        val jdKeywords = listOf("Python", "FastAPI")

        val categorized = SkillCategorizer.categorize(languages, jdKeywords)
        val sortedLanguages = categorized[SkillCategorizer.CAT_LANGUAGES].orEmpty()

        // Python should be first because it matches the target JD
        assertEquals("Python", sortedLanguages.first())
    }

    @Test
    fun neverAddsSkillsNotInInput() {
        val input = listOf("Kotlin")
        val categorized = SkillCategorizer.categorize(input)

        val allSkills = categorized.values.flatten()
        assertEquals(listOf("Kotlin"), allSkills)
    }
}
