package com.osrs.careerguild

import com.osrs.careerguild.core.location.JobLocationUtils
import com.osrs.careerguild.core.network.LinkValidator
import com.osrs.careerguild.core.profile.UserProfile
import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeCertification
import com.osrs.careerguild.core.resume.ResumeEducation
import com.osrs.careerguild.core.resume.ResumeExperience
import com.osrs.careerguild.core.resume.ResumeProject
import com.osrs.careerguild.data.repository.CourierRepository
import com.osrs.careerguild.data.repository.SkillRepositoryImpl
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.domain.resume.TailoredResume
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierAgentConfig
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierAutoApplyDispatcher
import com.osrs.careerguild.presentation.hub.CameraController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun `Test 5 - LinkedIn platform exclusion blocks LinkedIn source and URLs from auto-apply`() {
        assertTrue(CourierAutoApplyDispatcher.isExcludedFromAutoApply("LinkedIn Direct", "https://boards.greenhouse.io/stripe"))
        assertTrue(CourierAutoApplyDispatcher.isExcludedFromAutoApply("TechRadar", "https://www.linkedin.com/jobs/view/123456"))
        assertFalse(CourierAutoApplyDispatcher.isExcludedFromAutoApply("Greenhouse", "https://boards.greenhouse.io/stripe/jobs/7182934"))
    }

    @Test
    fun `Test 6 - Courier Vault strong password generator and encryption round-trip`() {
        val pwd = CourierAutoApplyDispatcher.generateStrongPassword(18)
        assertEquals(18, pwd.length)
        val encrypted = CourierAutoApplyDispatcher.encryptPassword(pwd)
        assertNotEquals(pwd, encrypted)
        val decrypted = CourierAutoApplyDispatcher.decryptPassword(encrypted)
        assertEquals(pwd, decrypted)
    }

    @Test
    fun `Test 7 - Modular Profile Toggling filters out inactive Experience, Projects, Education, and Certifications`() {
        val base = BaseResume(
            experience = listOf(
                ResumeExperience(employer = "Tyche Technologies", title = "Software Engineer", isActive = true),
                ResumeExperience(employer = "Mount Sinai", title = "Intern", isActive = false),
            ),
            projects = listOf(
                ResumeProject(name = "Grand Exchange Guild", isActive = true),
                ResumeProject(name = "Legacy Bot", isActive = false),
            ),
            education = listOf(
                ResumeEducation(school = "CUNY Queens College", degree = "B.A. CS", isActive = true),
                ResumeEducation(school = "Secondary School", degree = "Diploma", isActive = false),
            ),
            certifications = listOf(
                ResumeCertification(name = "IBM Data Engineering", isActive = true),
                ResumeCertification(name = "Inactive Cert", isActive = false),
            ),
        )

        val tailored = TailoredResume.fromBaseResume(base)
        assertEquals(1, tailored.experience.size)
        assertEquals("Tyche Technologies", tailored.experience.first().employer)
        assertEquals(1, tailored.projects.size)
        assertEquals("Grand Exchange Guild", tailored.projects.first().name)
        assertEquals(1, tailored.education.size)
        assertEquals("CUNY Queens College", tailored.education.first().school)
        assertEquals(1, tailored.certifications.size)
        assertEquals("IBM Data Engineering", tailored.certifications.first().name)
    }

    @Test
    fun `Test 8 - First-Person CameraController maps joystick X-Y deltas to translation and rotation vectors`() {
        val controller = CameraController()
        assertFalse(controller.isFirstPersonMode)
        controller.toggleFirstPersonMode()
        assertTrue(controller.isFirstPersonMode)

        val initialZ = controller.fpEyeZ
        val initialYaw = controller.fpYaw
        controller.updateJoystick(normX = 0.5f, normY = -0.8f) // push forward and right
        controller.stepFirstPersonMovement(dtSec = 0.1f)

        assertNotEquals(initialZ, controller.fpEyeZ)
        assertNotEquals(initialYaw, controller.fpYaw)
    }

    @Test
    fun `Test 9 - Execution Breakdown formatter and CourierRepository dynamic payload check`() {
        val breakdown = CourierAgentConfig.ExecutionBreakdown(
            status = "COMPLETED",
            atsPlatformDetected = "Greenhouse",
            mappingLog = listOf("first_name -> filled", "email -> filled"),
            unmappedAmbiguousFields = listOf("EEO dropdown"),
            failureAnalysisAndUpgradePath = "None",
        )
        val lines = breakdown.toConsoleLines("Cloudflare")
        assertEquals(6, lines.size)
        assertTrue(lines.any { it.contains("Status: COMPLETED") })
        assertTrue(lines.any { it.contains("ATS Platform Detected: Greenhouse") })

        val repo = CourierRepository()
        val validCheck = repo.verifyDynamicPayload(
            UserProfile(
                name = "Savaze",
                email = "savazework@gmail.com",
                github = "github.com/savazek",
                location = "11581",
            )
        )
        assertTrue(validCheck.isValid)
        assertTrue(validCheck.missingFields.isEmpty())
    }

    @Test
    fun `Test 10 - Heuristic Web Automation bootstrap and portal navigator inject neon amber bounding box, click ripple, and simulated typing`() {
        val bootstrapJs = CourierAgentConfig.buildHeuristicVisualOverlayBootstrapJs()
        assertTrue(bootstrapJs.contains("cg-amber-bbox"))
        assertTrue(bootstrapJs.contains("#FFB300"))
        assertTrue(bootstrapJs.contains("cg-click-ripple"))
        assertTrue(bootstrapJs.contains("__cgHeuristicTypeField"))

        val portalNavJs = CourierAgentConfig.buildHeuristicPortalNavigatorJs("Distributed Systems Engineer")
        assertTrue(portalNavJs.contains("distributed systems engineer"))
        assertTrue(portalNavJs.contains("__cgUpdateAmberBox"))
        assertTrue(portalNavJs.contains("__cgTriggerClickRipple"))

        val typingJs = CourierAgentConfig.buildVisualSimulatedTypingJs(
            selector = "input[name*='first_name']",
            keywordHints = listOf("first name"),
            fieldValue = "Savaze",
            displayName = "First Name",
        )
        assertTrue(typingJs.contains("__cgHeuristicTypeField"))
        assertTrue(typingJs.contains("First Name"))
    }

    @Test
    fun `Test 11 - Avatar-Centric 3D Navigation enforces environmental boundaries, prevents building clipping, and triggers Guild Hall entry`() {
        val controller = CameraController()
        controller.toggleFirstPersonMode()
        assertTrue(controller.isFirstPersonMode)

        // 1. Verify environmental boundary clamping (cannot walk outside MAX_PLAZA_RADIUS = 14.5f)
        controller.avatarX = 0f
        controller.avatarZ = -14.3f
        controller.avatarYaw = 0f // facing -Z
        controller.updateJoystick(normX = 0f, normY = -1.0f) // walk straight forward toward outer rim
        repeat(20) {
            controller.stepFirstPersonMovement(dtSec = 0.05f)
        }
        val radialDist = kotlin.math.hypot(controller.avatarX, controller.avatarZ)
        assertTrue("Avatar radial distance ($radialDist) must be clamped to <= ${CameraController.MAX_PLAZA_RADIUS}", radialDist <= CameraController.MAX_PLAZA_RADIUS + 0.01f)

        // 2. Verify physical contact with Courier Guild collider (7.2f, 7.2f) prevents clipping & triggers COURIER_GUILD entry
        controller.avatarX = 7.2f
        controller.avatarZ = 4.3f
        controller.avatarYaw = Math.PI.toFloat() // facing +Z toward (7.2, 7.2)
        controller.updateJoystick(normX = 0f, normY = -1.0f) // walk straight into Courier Guild collider
        var triggered: GuildBuilding? = null
        repeat(20) {
            val hit = controller.stepFirstPersonMovement(dtSec = 0.05f)
            if (hit != null) {
                triggered = hit
            }
        }
        assertNotNull("Touching Courier Guild collider must trigger building entry", triggered)
        assertEquals(GuildBuilding.COURIER_GUILD, triggered)

        // Verify avatar was pushed outside the building collider and did not clip inside
        val distFromBuilding = kotlin.math.hypot(controller.avatarX - 7.2f, controller.avatarZ - 7.2f)
        assertTrue("Avatar must not clip inside building collider (dist=$distFromBuilding)", distFromBuilding > CameraController.BUILDING_COLLIDER_RADIUS)
    }
}
