package com.osrs.careerguild

import com.osrs.careerguild.core.location.JobLocationUtils
import com.osrs.careerguild.core.network.LinkValidator
import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeExperience
import com.osrs.careerguild.data.repository.SkillRepositoryImpl
import com.osrs.careerguild.domain.resume.TailoredResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon4AuditTest {

    @Test
    fun `Test 1 - ScribeTower sanitizes glued month collision between Tyche Technologies and August`() {
        val rawExp = ResumeExperience(
            employer = "Tyche TechnologiesAugust",
            title = "Junior Software Engineer",
            startDate = "2023",
            endDate = "Present",
            isCurrent = true,
            bullets = listOf(
                "Worked as a consultant on AI-focused software projects using Python and JavaScript.",
                "Built and maintained automated API-driven systems and data pipelines."
            )
        )

        val sanitized = TailoredResume.sanitizeExperience(rawExp)
        assertEquals("Tyche Technologies", sanitized.employer)
        assertEquals("Junior Software Engineer", sanitized.title)
        assertEquals("August 2023", sanitized.startDate)
        assertEquals("Present", sanitized.endDate)
    }

    @Test
    fun `Test 2 - SkillAltar set-union normalization deduplicates aliases and auto-categorizes`() {
        val rawExtractedSkills = listOf(
            "Python",
            "python 3",
            "JS",
            "JavaScript (ES6+)",
            "PostgreSQL",
            "postgres",
            "AI-Assisted Workflows",
            "AI Agents",
            "Docker",
            "Playwright"
        )

        val normalizedSet = linkedSetOf<String>()
        for (raw in rawExtractedSkills) {
            normalizedSet.add(SkillRepositoryImpl.normalizeSkillKey(raw))
        }

        // Verify strict set-union deduplication
        assertEquals(
            setOf("python", "javascript", "postgresql", "ai agents", "docker", "playwright"),
            normalizedSet
        )

        // Verify auto-categorization into the 3 core Skill Altar categories
        assertEquals("AI & Agents", SkillRepositoryImpl.inferCategory("AI Agents"))
        assertEquals("Automation & Web", SkillRepositoryImpl.inferCategory("Playwright"))
        assertEquals("Automation & Web", SkillRepositoryImpl.inferCategory("Docker"))
        assertEquals("Backend & Data", SkillRepositoryImpl.inferCategory("PostgreSQL"))
        assertEquals("Backend & Data", SkillRepositoryImpl.inferCategory("Python"))
    }

    @Test
    fun `Test 3 - BountyBoard strict ATS link validator rejects search fallbacks and dead links`() {
        assertTrue(LinkValidator.isValidDirectAtsUrl("https://boards.greenhouse.io/stripe/jobs/7182934", "VERIFIED"))
        assertTrue(LinkValidator.isValidDirectAtsUrl("https://jobs.lever.co/palantir/a1b2c3d4-e5f6-7890-abcd-ef1234567890", "VERIFIED"))
        assertFalse(LinkValidator.isValidDirectAtsUrl("https://jobs.lever.co/palantir/fake-slug", "VERIFIED"))
        assertFalse(LinkValidator.isValidDirectAtsUrl("https://www.google.com/search?q=software+engineer", "VERIFIED"))
        assertFalse(LinkValidator.isValidDirectAtsUrl("https://boards.greenhouse.io/stripe/jobs/7182934", "DEAD"))
        assertFalse(LinkValidator.isValidDirectAtsUrl("https://boards.greenhouse.io/stripe/jobs/7182934", "SEED"))
    }

    @Test
    fun `Test 4 - Algorithmic sorting telemetry formats recency, Haversine distance from 11581, and match score`() {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 3_600_000L)
        val badge = JobLocationUtils.formatAlgorithmicMetrics(
            timePosted = twoHoursAgo,
            jobLocation = "New York, NY",
            workMode = "Hybrid",
            userZip = "11581",
            matchScore = 92,
        )
        assertTrue(badge.contains("Posted 2h ago"))
        assertTrue(badge.contains("mi away"))
        assertTrue(badge.contains("92% Match"))
    }
}
