package com.osrs.careerguild.domain.resume

import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeBulletMetric
import com.osrs.careerguild.core.resume.ResumeExperience
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ResumeVerifier] enforcement of real user metrics and date invariants.
 * Uses 100% synthetic fixtures per GEMINI.md hard invariant.
 */
class ResumeVerifierMetricsAndDatesTest {

    @Test
    fun userProvidedMetric_permitsNumberInTailoredBullet() {
        val baseExp = ResumeExperience(
            employer = "Synthetic Cloud Corp",
            title = "Infrastructure Engineer",
            startDate = "Jan 2022",
            endDate = "Present",
            isCurrent = true,
            isConfirmedCurrent = true,
            bullets = listOf("Streamlined distributed cache ingestion workflows."),
            bulletMetrics = listOf(
                ResumeBulletMetric(
                    bulletText = "Streamlined distributed cache ingestion workflows.",
                    userMetric = "45% faster throughput",
                    userProvided = true,
                )
            ),
            userMetrics = mapOf("Streamlined distributed cache ingestion workflows." to "45% faster throughput"),
        )
        val baseResume = BaseResume(experience = listOf(baseExp))

        val tailoredExp = baseExp.copy(
            bullets = listOf("Streamlined distributed cache ingestion workflows, achieving 45% faster throughput.")
        )
        val tailoredResume = TailoredResume(
            experience = listOf(tailoredExp),
            isTailored = true,
        )

        val result = ResumeVerifier.verify(tailoredResume, baseResume, emptyList())
        assertTrue("Expected user-provided metric 45% to be allowed, violations: ${result.violations}", result.isValid)
    }

    @Test
    fun fabricatedMetricWithoutUserProvidedMetric_isRejected() {
        val baseExp = ResumeExperience(
            employer = "Synthetic Cloud Corp",
            title = "Infrastructure Engineer",
            startDate = "Jan 2022",
            endDate = "Present",
            isCurrent = true,
            isConfirmedCurrent = true,
            bullets = listOf("Streamlined distributed cache ingestion workflows."),
        )
        val baseResume = BaseResume(experience = listOf(baseExp))

        val fabricatedExp = baseExp.copy(
            bullets = listOf("Streamlined distributed cache ingestion workflows, improving latency by 85%.")
        )
        val tailoredResume = TailoredResume(
            experience = listOf(fabricatedExp),
            isTailored = true,
        )

        val result = ResumeVerifier.verify(tailoredResume, baseResume, emptyList())
        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("Fabricated metric/number '85'") })
    }

    @Test
    fun alteredDates_areRejectedByVerifier() {
        val baseExp = ResumeExperience(
            employer = "Synthetic Data Inc",
            title = "Data Analyst",
            startDate = "Mar 2021",
            endDate = "Nov 2023",
            isCurrent = false,
            bullets = listOf("Built data processing workflows in SQL."),
        )
        val baseResume = BaseResume(experience = listOf(baseExp))

        val alteredExp = baseExp.copy(startDate = "Jan 2021") // Changed start date
        val tailoredResume = TailoredResume(
            experience = listOf(alteredExp),
            isTailored = true,
        )

        val result = ResumeVerifier.verify(tailoredResume, baseResume, emptyList())
        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("Modified start date 'Jan 2021'") })
    }
}
