package com.osrs.careerguild.presentation.dashboard.courierguild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourierApplyTriggerFinderTest {

    private fun control(
        elementId: String,
        tag: String = "a",
        type: String = "",
        name: String = "",
        id: String = "",
        label: String = "",
        hrefPath: String = "",
        containerHint: String = "main",
    ) = DomControlSnapshot(
        elementId = elementId,
        tag = tag,
        type = type,
        name = name,
        id = id,
        label = label,
        placeholder = "",
        options = emptyList(),
        required = false,
        maxLength = 0,
        containerHint = containerHint,
        hrefPath = hrefPath,
        currentValuePresent = false,
    )

    @Test
    fun `selects primary Apply CTA and rejects newsletter subscribe trap`() {
        val controls = listOf(
            control(
                elementId = "cg_el_0",
                tag = "button",
                id = "onetrust-reject-btn-handler",
                label = "Reject Non-Essential Cookies",
                containerHint = "div#onetrust-banner-sdk.cookie-banner",
            ),
            control(
                elementId = "cg_el_1",
                tag = "a",
                label = "Email Recruiting",
                hrefPath = "mailto:recruiting@example.invalid",
                containerHint = "header.site-header",
            ),
            control(
                elementId = "cg_el_2",
                tag = "a",
                id = "job-apply-btn",
                label = "Apply for this job",
                hrefPath = "https://boards.greenhouse.io/acme/jobs/12345#app",
                containerHint = "main.job-posting",
            ),
            control(
                elementId = "cg_el_3",
                tag = "button",
                id = "newsletter-submit",
                label = "Subscribe & Apply",
                containerHint = "aside.newsletter-modal.subscribe-banner",
            ),
            control(
                elementId = "cg_el_4",
                tag = "a",
                label = "Share on X",
                hrefPath = "https://twitter.com/share",
                containerHint = "footer",
            ),
        )

        val evaluation = CourierApplyTriggerFinder.selectApplyTrigger(
            controls = controls,
            currentUrl = "https://careers.acme.invalid/jobs/backend-engineer",
        )

        assertNotNull(evaluation.selectedCandidate)
        assertEquals("cg_el_2", evaluation.selectedCandidate?.elementId)
        assertTrue((evaluation.scoredCandidates.firstOrNull()?.score ?: 0) >= CourierApplyTriggerFinder.MIN_PASSING_SCORE)

        // Cookie banner button must be detected as a cookie dismiss candidate, never an apply trigger
        val cookieDismiss = CourierApplyTriggerFinder.findCookieBannerDismiss(controls)
        assertNotNull(cookieDismiss)
        assertEquals("cg_el_0", cookieDismiss?.elementId)
    }

    @Test
    fun `rejects job alert, talent community, mailto, tel, and newsletter links`() {
        val controls = listOf(
            control(
                elementId = "cg_alert",
                tag = "button",
                label = "Create Job Alert & Apply",
                containerHint = "div.job-alert-box",
            ),
            control(
                elementId = "cg_talent",
                tag = "a",
                label = "Join our Talent Community",
                hrefPath = "https://careers.acme.invalid/talent-community",
                containerHint = "section.talent-community",
            ),
            control(
                elementId = "cg_tel",
                tag = "a",
                label = "Apply Now by Phone",
                hrefPath = "tel:+18005550199",
                containerHint = "main",
            ),
            control(
                elementId = "cg_offsite_apply",
                tag = "a",
                label = "Apply Now",
                hrefPath = "https://unrelated-social-tracker.invalid/apply",
                containerHint = "footer#site-footer",
            ),
        )

        val evaluation = CourierApplyTriggerFinder.selectApplyTrigger(
            controls = controls,
            currentUrl = "https://careers.acme.invalid/jobs/101",
        )

        assertNull(evaluation.selectedCandidate)
        assertTrue(evaluation.scoredCandidates.isEmpty())
    }

    @Test
    fun `detects when application form is already visible on page`() {
        val formControls = listOf(
            control(
                elementId = "cg_file",
                tag = "input",
                type = "file",
                name = "resume",
                label = "Resume/CV *",
                containerHint = "form#application_form",
            ),
            control(
                elementId = "cg_fn",
                tag = "input",
                type = "text",
                name = "first_name",
                label = "First Name *",
                containerHint = "form#application_form",
            ),
        )

        val evaluation = CourierApplyTriggerFinder.selectApplyTrigger(
            controls = formControls,
            currentUrl = "https://boards.greenhouse.io/acme/jobs/12345",
        )

        assertTrue(evaluation.formAlreadyVisible)
    }

    @Test
    fun `requests model tie-break when top two candidates are within ambiguity threshold`() {
        val controls = listOf(
            control(
                elementId = "cg_ambig_1",
                tag = "button",
                id = "apply_cta_one",
                label = "Apply Online",
                containerHint = "main",
            ),
            control(
                elementId = "cg_ambig_2",
                tag = "button",
                id = "apply_cta_two",
                label = "Apply Direct",
                containerHint = "main",
            ),
        )

        val evaluation = CourierApplyTriggerFinder.selectApplyTrigger(
            controls = controls,
            currentUrl = "https://careers.acme.invalid/role/42",
        )

        assertNull(evaluation.selectedCandidate)
        assertTrue(evaluation.needsModelTieBreak)
        assertEquals(2, evaluation.scoredCandidates.size)
    }
}
