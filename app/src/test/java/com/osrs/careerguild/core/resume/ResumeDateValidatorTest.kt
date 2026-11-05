package com.osrs.careerguild.core.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ResumeDateValidator] date sequence, format checks, and concurrency.
 * Uses 100% synthetic fixtures per GEMINI.md hard invariant.
 */
class ResumeDateValidatorTest {

    @Test
    fun consistentMonYyyyDates_validatesCleanly() {
        val syntheticExp = listOf(
            ResumeExperience(
                employer = "Synthetic Tech Corp",
                title = "Backend Engineer",
                startDate = "Jan 2022",
                endDate = "Present",
                isCurrent = true,
                isConfirmedCurrent = true,
            ),
            ResumeExperience(
                employer = "Synthetic Solutions LLC",
                title = "Junior Developer",
                startDate = "Jun 2020",
                endDate = "Dec 2021",
                isCurrent = false,
            )
        )

        val result = ResumeDateValidator.validateExperienceDates(syntheticExp)
        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun invertedDates_flagsSequenceIssue() {
        val invertedExp = listOf(
            ResumeExperience(
                employer = "Synthetic Labs Inc",
                title = "Systems Architect",
                startDate = "Aug 2023",
                endDate = "Jan 2023", // End precedes start
                isCurrent = false,
            )
        )

        val result = ResumeDateValidator.validateExperienceDates(invertedExp)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("cannot precede start date") })
    }

    @Test
    fun inconsistentDateFormat_flagsFormattingNotice() {
        val nonStandardExp = listOf(
            ResumeExperience(
                employer = "Synthetic AI Systems",
                title = "Machine Learning Engineer",
                startDate = "2022-01", // Non 'Mon YYYY' format
                endDate = "May 2023",
                isCurrent = false,
            )
        )

        val result = ResumeDateValidator.validateExperienceDates(nonStandardExp)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("Mon YYYY") })
    }

    @Test
    fun overlappingConcurrentRoles_areAllowed() {
        val concurrentRoles = listOf(
            ResumeExperience(
                employer = "Synthetic Company Alpha",
                title = "Senior Consultant",
                startDate = "Jan 2021",
                endDate = "Present",
                isCurrent = true,
                isConfirmedCurrent = true,
            ),
            ResumeExperience(
                employer = "Synthetic Venture Beta",
                title = "Advisory Engineer",
                startDate = "Mar 2021",
                endDate = "Present",
                isCurrent = true,
                isConfirmedCurrent = true,
            )
        )

        val result = ResumeDateValidator.validateExperienceDates(concurrentRoles)
        assertTrue(result.isValid)
        assertEquals(0, result.issues.size)
    }
}
