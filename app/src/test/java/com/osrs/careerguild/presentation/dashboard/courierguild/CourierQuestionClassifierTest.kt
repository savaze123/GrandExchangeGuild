package com.osrs.careerguild.presentation.dashboard.courierguild

import com.osrs.careerguild.presentation.dashboard.courierguild.CourierResponseSynthesizer.QuestionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourierQuestionClassifierTest {

    @Test
    fun `classifies EEO gender, race, and veteran questions into EEO_PROFILE`() {
        assertEquals(
            QuestionCategory.EEO_PROFILE,
            CourierResponseSynthesizer.classifyCategory("Gender *"),
        )
        assertEquals(
            QuestionCategory.EEO_PROFILE,
            CourierResponseSynthesizer.classifyCategory("Please identify your race / ethnicity"),
        )
        assertEquals(
            QuestionCategory.EEO_PROFILE,
            CourierResponseSynthesizer.classifyCategory("Protected Veteran Status"),
        )
    }

    @Test
    fun `classifies legal, work authorization, sponsorship, disability, criminal history, and salary into PROTECTED`() {
        assertEquals(
            QuestionCategory.PROTECTED,
            CourierResponseSynthesizer.classifyCategory("Are you legally authorized to work in the United States?"),
        )
        assertEquals(
            QuestionCategory.PROTECTED,
            CourierResponseSynthesizer.classifyCategory("Will you now or in the future require visa sponsorship (e.g. H-1B)?"),
        )
        assertEquals(
            QuestionCategory.PROTECTED,
            CourierResponseSynthesizer.classifyCategory("Voluntary Self-Identification of Disability"),
        )
        assertEquals(
            QuestionCategory.PROTECTED,
            CourierResponseSynthesizer.classifyCategory("Have you ever been convicted of a felony or criminal offense?"),
        )
        assertEquals(
            QuestionCategory.PROTECTED,
            CourierResponseSynthesizer.classifyCategory("What is your desired salary / compensation expectation?"),
        )
    }

    @Test
    fun `classifies why company, why role, and cover letter into MOTIVATION`() {
        assertEquals(
            QuestionCategory.MOTIVATION,
            CourierResponseSynthesizer.classifyCategory("Why do you want to work at SyntheticCorp?"),
        )
        assertEquals(
            QuestionCategory.MOTIVATION,
            CourierResponseSynthesizer.classifyCategory("What excites you most about this role?"),
        )
        assertEquals(
            QuestionCategory.MOTIVATION,
            CourierResponseSynthesizer.classifyCategory("Cover Letter (optional)"),
        )
    }

    @Test
    fun `classifies projects, technical challenges, and background into EXPERIENCE`() {
        assertEquals(
            QuestionCategory.EXPERIENCE,
            CourierResponseSynthesizer.classifyCategory("Tell us about a project you have built and the technical challenges you solved."),
        )
        assertEquals(
            QuestionCategory.EXPERIENCE,
            CourierResponseSynthesizer.classifyCategory("How many years of experience do you have with Kotlin and distributed systems?"),
        )
        assertEquals(
            QuestionCategory.EXPERIENCE,
            CourierResponseSynthesizer.classifyCategory("Tell us about yourself and your engineering background."),
        )
    }

    @Test
    fun `classifies standard contact and logistics into STANDARD_PERSONAL`() {
        assertEquals(
            QuestionCategory.STANDARD_PERSONAL,
            CourierResponseSynthesizer.classifyCategory("First Name"),
        )
        assertEquals(
            QuestionCategory.STANDARD_PERSONAL,
            CourierResponseSynthesizer.classifyCategory("LinkedIn Profile URL"),
        )
        assertEquals(
            QuestionCategory.STANDARD_PERSONAL,
            CourierResponseSynthesizer.classifyCategory("When is your earliest available start date?"),
        )
    }

    @Test
    fun `matches portal EEO options by normalized exact text and deterministic decline synonyms`() {
        val portalGenderOptions = listOf("Male", "Female", "Decline to self-identify")
        assertEquals(
            "Decline to self-identify",
            CourierPipelineExecutor.matchPortalEeoOption(
                portalGenderOptions,
                CourierResponseSynthesizer.DEFAULT_GENDER,
            ),
        )

        val portalRaceSynonymOptions = listOf(
            "Hispanic or Latino",
            "White",
            "Black",
            "Asian",
            "I do not wish to answer",
        )
        assertEquals(
            "I do not wish to answer",
            CourierPipelineExecutor.matchPortalEeoOption(
                portalRaceSynonymOptions,
                CourierResponseSynthesizer.DEFAULT_RACE,
            ),
        )

        val portalUnmatchedOptions = listOf("Option A", "Option B")
        assertNull(
            CourierPipelineExecutor.matchPortalEeoOption(
                portalUnmatchedOptions,
                "Female",
            ),
        )
    }
}
