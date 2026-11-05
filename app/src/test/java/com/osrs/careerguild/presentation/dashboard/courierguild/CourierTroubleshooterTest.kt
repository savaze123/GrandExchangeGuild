package com.osrs.careerguild.presentation.dashboard.courierguild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourierTroubleshooterTest {

    private fun mockControl(
        elementId: String,
        tag: String = "input",
        type: String = "text",
        name: String = "",
        id: String = "",
        label: String = "",
        placeholder: String = "",
        containerHint: String = "",
        hrefPath: String = "",
        required: Boolean = false,
    ) = DomControlSnapshot(
        elementId = elementId,
        tag = tag,
        type = type,
        name = name,
        id = id,
        label = label,
        placeholder = placeholder,
        options = emptyList(),
        required = required,
        maxLength = 0,
        containerHint = containerHint,
        hrefPath = hrefPath,
        currentValuePresent = false,
    )

    @Test
    fun troubleshooterCategory_fromString_parsesCorrectly() {
        assertEquals(TroubleshooterCategory.GATEKEEPER, TroubleshooterCategory.fromString("GATEKEEPER"))
        assertEquals(TroubleshooterCategory.GATEKEEPER, TroubleshooterCategory.fromString("gatekeeper"))
        assertEquals(TroubleshooterCategory.GUEST_FLOW, TroubleshooterCategory.fromString("GUEST_FLOW"))
        assertEquals(TroubleshooterCategory.APPLICATION_FORM, TroubleshooterCategory.fromString("application_form"))
        assertEquals(TroubleshooterCategory.HARD_BLOCK, TroubleshooterCategory.fromString("HARD_BLOCK"))
        assertNull(TroubleshooterCategory.fromString("UNKNOWN"))
        assertNull(TroubleshooterCategory.fromString(null))
    }

    @Test
    fun stageA_guestApply_prioritizedOverLoginWall() {
        val loginWithGuestOptions = listOf(
            mockControl("email", tag = "input", type = "email", name = "username", label = "Email"),
            mockControl("pwd", tag = "input", type = "password", name = "password", label = "Password"),
            mockControl("login_btn", tag = "button", label = "Sign In"),
            mockControl("guest_btn", tag = "button", label = "Apply without account", containerHint = "div.alt-apply"),
        )

        val guestTrigger = CourierGuardrails.findGuestApplyTrigger(loginWithGuestOptions)
        assertNotNull(guestTrigger)
        assertEquals("guest_btn", guestTrigger!!.elementId)
    }

    @Test
    fun stageA_resumeDropzone_detectedEvenWithLoginFormPresent() {
        val loginWithDropzone = listOf(
            mockControl("pwd", tag = "input", type = "password", name = "password", label = "Password"),
            mockControl("login_btn", tag = "button", label = "Sign In"),
            mockControl("resume_drop", tag = "input", type = "file", label = "Upload Resume to start"),
        )

        val fileInput = CourierGuardrails.findResumeFileInput(loginWithDropzone)
        assertNotNull(fileInput)
        assertEquals("resume_drop", fileInput!!.elementId)
    }

    @Test
    fun stageB_multiStepGatekeeper_identifiedCorrectly() {
        val gatekeeperControls = listOf(
            mockControl("g_email", tag = "input", type = "email", name = "email", label = "Enter Email to Begin", required = true),
            mockControl("g_first", tag = "input", type = "text", name = "first_name", label = "First Name", required = true),
            mockControl("g_next", tag = "button", label = "Next", containerHint = "div.actions"),
        )

        assertTrue(CourierGuardrails.isMultiStepGatekeeper(gatekeeperControls))
        assertEquals("g_next", CourierGuardrails.findGatekeeperNextButton(gatekeeperControls)?.elementId)

        // If a password field is present, it is an auth screen, NOT a multi-step gatekeeper
        val authControls = gatekeeperControls + mockControl("g_pwd", tag = "input", type = "password", label = "Password")
        assertFalse(CourierGuardrails.isMultiStepGatekeeper(authControls))
    }

    @Test
    fun quarantine_hardBansJobDescriptionShortenersAndNewsletterTraps() {
        val tinyUrlLink = mockControl(
            elementId = "link_tiny",
            tag = "a",
            label = "Company Perks",
            hrefPath = "https://tinyurl.com/perks-doc",
            containerHint = "div.job-description",
        )
        assertTrue(CourierApplyTriggerFinder.isHardExcluded(tinyUrlLink))

        val lnkInLink = mockControl(
            elementId = "link_lnkd",
            tag = "a",
            label = "Our Team",
            hrefPath = "https://lnkd.in/our-team",
            containerHint = "p.description",
        )
        assertTrue(CourierApplyTriggerFinder.isHardExcluded(lnkInLink))

        val newsletterInput = mockControl(
            elementId = "input_news",
            tag = "input",
            type = "email",
            name = "subscriber_email",
            label = "Sign up for job alerts and news",
            containerHint = "footer.site-footer form.newsletter-form",
        )
        assertTrue(CourierApplyTriggerFinder.isNewsletterOrMarketingTrap(newsletterInput))
    }
}
