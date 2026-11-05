package com.osrs.careerguild.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.osrs.careerguild.CareerGuildApp
import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeContact
import com.osrs.careerguild.core.resume.ResumeEducation
import com.osrs.careerguild.core.resume.ResumeExperience
import com.osrs.careerguild.core.resume.ResumeProject
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import com.osrs.careerguild.di.DatabaseModule
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierBlockedReason
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierPipelineExecutor
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierQueueWorker
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierTaskStatus
import com.osrs.careerguild.presentation.dashboard.courierguild.SubmitPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourierMigrationTest {

    private val testDbName = "career_guild_migration_test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CareerGuildDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate5To6PreservesExistingRowsAndCreatesCourierTables() {
        val dbV5 = helper.createDatabase(testDbName, 5)
        dbV5.execSQL(
            """
            INSERT INTO jobs (id, title, company, ats_url, source, score, matched_skills, is_blocked, applied_at, location, work_mode, apply_url, description, link_status)
            VALUES ('job_synthetic_1', 'Backend Engineer', 'SyntheticCorp', 'https://boards.greenhouse.io/synthetic/jobs/1', 'Greenhouse', 92, 'Kotlin|PostgreSQL', 0, NULL, 'Remote', 'Remote', 'https://boards.greenhouse.io/synthetic/jobs/1', 'Build services', 'VERIFIED')
            """.trimIndent()
        )
        dbV5.execSQL(
            """
            INSERT INTO applicant_answers (question_pattern, answer, updated_at)
            VALUES ('work_authorization', 'Yes', 1700000000000)
            """.trimIndent()
        )
        dbV5.close()

        val dbV6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            DatabaseModule.MIGRATION_5_6,
        )

        // Verify existing rows in v5 tables survived Migration 5 -> 6
        dbV6.query("SELECT id, title, company FROM jobs WHERE id = 'job_synthetic_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("job_synthetic_1", cursor.getString(0))
            assertEquals("Backend Engineer", cursor.getString(1))
            assertEquals("SyntheticCorp", cursor.getString(2))
        }

        dbV6.query("SELECT answer FROM applicant_answers WHERE question_pattern = 'work_authorization'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Yes", cursor.getString(0))
        }

        // Verify new v6 tables accept inserts with column defaults
        dbV6.execSQL(
            """
            INSERT INTO courier_task (task_id, job_id, job_url, ats, status)
            VALUES ('task_synthetic_1', 'job_synthetic_1', 'https://boards.greenhouse.io/synthetic/jobs/1', 'greenhouse', 'QUEUED')
            """.trimIndent()
        )
        dbV6.query("SELECT status, submit_policy, attempts FROM courier_task WHERE task_id = 'task_synthetic_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("QUEUED", cursor.getString(0))
            assertEquals("REVIEW_BEFORE_SUBMIT", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }

        dbV6.close()
    }

    @Test
    fun fixtureWebHostAndPipelineVerification() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CareerGuildApp

        // Purge any legacy test artifacts from the production AppContainer database
        app.appContainer.baseResumeRepository.getBaseResume()
        app.appContainer.tailoredResumeStore.deleteTailoredResume("job_apply_1")
        app.appContainer.courierTaskDao.getAll().forEach { t ->
            if (t.taskId.startsWith("fixture_") || t.taskId.startsWith("crash_") || t.company.startsWith("Synthetic")) {
                app.appContainer.courierTaskDao.deleteById(t.taskId)
            }
        }

        // Create an isolated in-memory Room database and temporary directory so the live user DB is never touched
        val testDb = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            CareerGuildDatabase::class.java,
        ).allowMainThreadQueries().build()
        val testTailoredDir = java.io.File(context.cacheDir, "test_tailored_resumes_${System.currentTimeMillis()}").apply { mkdirs() }
        val container = com.osrs.careerguild.di.AppContainer(
            context = context,
            databaseOverride = testDb,
            tailoredResumeDirOverride = testTailoredDir,
            allowTestFixtures = true,
        )
        val webHost = container.courierWebHost

        try {
            // 1. Seed a 100% synthetic BaseResume in the isolated in-memory test database only
            val syntheticBase = BaseResume(
                contact = ResumeContact(
                    name = "Alex Mercer",
                    email = "alex.mercer@synthetic.invalid",
                    phone = "555-010-9988",
                    location = "Austin, TX",
                    github = "https://github.com/alexmercer-synth",
                ),
                summary = "Backend software engineer focused on Kotlin, PostgreSQL, and distributed systems.",
                skills = listOf("Kotlin", "PostgreSQL", "Coroutines", "Docker"),
                experience = listOf(
                    ResumeExperience(
                        employer = "SyntheticCloud",
                        title = "Software Engineer",
                        startDate = "2022",
                        endDate = "Present",
                        isCurrent = true,
                        bullets = listOf(
                            "Built Kotlin and PostgreSQL microservices handling 1200 requests per second with Docker."
                        ),
                    )
                ),
                projects = listOf(
                    ResumeProject(
                        name = "QueryFlow",
                        technologies = listOf("Kotlin", "Coroutines"),
                        bullets = listOf(
                            "Designed an offline-first query engine solving concurrency challenges with Coroutines."
                        ),
                    )
                ),
                education = listOf(
                    ResumeEducation(
                        school = "Synthetic State University",
                        degree = "B.S. in Computer Science",
                    )
                ),
            )
            container.baseResumeRepository.saveConfirmedResume(
                rawText = "Alex Mercer\nSoftware Engineer | SyntheticCloud | 2022 - Present\nBuilt Kotlin and PostgreSQL microservices handling 1200 requests per second with Docker.\nQueryFlow | Kotlin, Coroutines\nB.S. in Computer Science | Synthetic State University",
                resume = syntheticBase,
            )

            // 2. Verify Challenge Page Fixture -> blocks with CAPTCHA_OR_CHALLENGE
            val challengeTask = CourierTaskEntity(
                taskId = "fixture_challenge_1",
                jobId = "job_challenge_1",
                jobUrl = "https://appassets.androidplatform.net/assets/courier_fixtures/challenge_page.html",
                ats = "greenhouse",
                title = "Backend Engineer",
                company = "SyntheticChallengeCo",
                status = CourierTaskStatus.NAVIGATING.name,
            )
            webHost.acquireSlot(challengeTask)
            webHost.loadUrlAndWait(challengeTask.taskId, challengeTask.jobUrl)
            val challengeBlock = CourierPipelineExecutor.checkEarlyBlocksAndLogin(
                task = challengeTask,
                webHost = webHost,
                container = container,
            )
            assertNotNull("Expected challenge page to trigger early block", challengeBlock)
            assertEquals(CourierBlockedReason.CAPTCHA_OR_CHALLENGE, challengeBlock?.reason)
            webHost.releaseSlot(challengeTask.taskId)

            // 3. Verify Login Wall Fixture (without credentials) -> blocks with LOGIN_REQUIRED
            val loginTask = CourierTaskEntity(
                taskId = "fixture_login_1",
                jobId = "job_login_1",
                jobUrl = "https://appassets.androidplatform.net/assets/courier_fixtures/login_wall.html",
                ats = "greenhouse",
                title = "Backend Engineer",
                company = "SyntheticLoginCo",
                status = CourierTaskStatus.NAVIGATING.name,
            )
            webHost.acquireSlot(loginTask)
            webHost.loadUrlAndWait(loginTask.taskId, loginTask.jobUrl)
            val loginBlock = CourierPipelineExecutor.checkEarlyBlocksAndLogin(
                task = loginTask,
                webHost = webHost,
                container = container,
            )
            assertNotNull("Expected login wall without vault credentials to block", loginBlock)
            assertEquals(CourierBlockedReason.LOGIN_REQUIRED, loginBlock?.reason)
            webHost.releaseSlot(loginTask.taskId)

            // 4. Verify Newsletter + Apply -> ATS Form Fixture (Forge -> Navigate -> Upload -> Overwrite) on isolated container
            com.osrs.careerguild.presentation.dashboard.courierguild.CourierAgentConfig.fieldActionDelayMinMs = 50L
            com.osrs.careerguild.presentation.dashboard.courierguild.CourierAgentConfig.fieldActionDelayMaxMs = 100L

            val uniqueTaskId = "fixture_apply_${System.currentTimeMillis()}"
            var applyTask = CourierTaskEntity(
                taskId = uniqueTaskId,
                jobId = "job_apply_1",
                jobUrl = "https://appassets.androidplatform.net/assets/courier_fixtures/newsletter_and_apply.html",
                ats = "greenhouse",
                title = "Software Engineer",
                company = "SyntheticApplyCo",
                status = CourierTaskStatus.QUEUED.name,
                submitPolicy = SubmitPolicy.REVIEW_BEFORE_SUBMIT.name,
            )
            container.courierTaskDao.upsert(applyTask)

            // Step 4a: FORGING
            applyTask = com.osrs.careerguild.presentation.dashboard.courierguild.CourierStateMachine.transition(
                task = applyTask,
                targetStatus = CourierTaskStatus.FORGING,
                step = "FORGE_AND_VERIFY_RESUME",
            )
            container.courierTaskDao.upsert(applyTask)
            val forgeOutcome = CourierPipelineExecutor.ensureVerifiedForgeResume(
                context = context,
                task = applyTask,
                container = container,
            )
            assertNotNull("Expected verified Forge PDF to be generated", forgeOutcome.pdfFile)
            assertTrue(forgeOutcome.pdfFile!!.exists())

            // Step 4b: NAVIGATING
            applyTask = com.osrs.careerguild.presentation.dashboard.courierguild.CourierStateMachine.transition(
                task = applyTask.copy(resumePdfPath = forgeOutcome.pdfFile!!.absolutePath),
                targetStatus = CourierTaskStatus.NAVIGATING,
                step = "LOAD_PORTAL",
            )
            container.courierTaskDao.upsert(applyTask)
            webHost.acquireSlot(applyTask)
            webHost.loadUrlAndWait(applyTask.taskId, applyTask.jobUrl)
            val navOutcome = CourierPipelineExecutor.navigateToApplicationForm(
                task = applyTask,
                webHost = webHost,
                courierModel = container.courierModel,
            )
            assertTrue("Expected Apply trigger navigation to reach ATS form", navOutcome.formReached)

            // Step 4c: FILLING -> AWAITING_REVIEW
            applyTask = com.osrs.careerguild.presentation.dashboard.courierguild.CourierStateMachine.transition(
                task = applyTask,
                targetStatus = CourierTaskStatus.FILLING,
                step = "UPLOAD_RESUME_FIRST",
                currentUrl = navOutcome.currentUrl,
            )
            container.courierTaskDao.upsert(applyTask)
            val filledMap = mutableMapOf<String, String>()
            val fillOutcome = CourierPipelineExecutor.uploadAndFillForm(
                context = context,
                task = applyTask,
                forgePdfFile = forgeOutcome.pdfFile!!,
                tailoredResume = forgeOutcome.tailoredResume,
                filledFields = filledMap,
                webHost = webHost,
                container = container,
            )
            webHost.releaseSlot(applyTask.taskId)
            val filledJson = org.json.JSONObject(filledMap as Map<*, *>).toString()
            applyTask = com.osrs.careerguild.presentation.dashboard.courierguild.CourierStateMachine.transition(
                task = applyTask.copy(
                    filledFieldsJson = filledJson,
                    reviewRequired = fillOutcome.reviewRequired,
                ),
                targetStatus = CourierTaskStatus.AWAITING_REVIEW,
                step = "READY_FOR_USER_REVIEW",
                currentUrl = fillOutcome.currentUrl,
            )
            container.courierTaskDao.upsert(applyTask)

            val finalTask = container.courierTaskDao.getById(applyTask.taskId)
            assertNotNull("Expected task $uniqueTaskId in isolated test DB", finalTask)
            assertEquals(CourierTaskStatus.AWAITING_REVIEW.name, finalTask?.status)
            assertTrue(
                "Expected filledFieldsJson to contain overwritten first_name and not WrongAutofillFirst",
                finalTask?.filledFieldsJson.orEmpty().contains("first_name") &&
                    !finalTask?.filledFieldsJson.orEmpty().contains("WrongAutofillFirst")
            )

            // 5. Verify Startup Crash Recovery on Room rows (SUBMITTING -> UNCONFIRMED, FILLING -> QUEUED with attempts + 1)
            val crashSubmitting = CourierTaskEntity(
                taskId = "crash_submitting_1",
                jobId = "job_crash_1",
                jobUrl = "https://appassets.androidplatform.net/assets/courier_fixtures/ats_form.html",
                ats = "greenhouse",
                title = "SRE",
                company = "SyntheticCrashCo",
                status = CourierTaskStatus.SUBMITTING.name,
                attempts = 0,
            )
            val crashFilling = CourierTaskEntity(
                taskId = "crash_filling_1",
                jobId = "job_crash_2",
                jobUrl = "https://appassets.androidplatform.net/assets/courier_fixtures/ats_form.html",
                ats = "greenhouse",
                title = "Backend Engineer",
                company = "SyntheticRetryCo",
                status = CourierTaskStatus.FILLING.name,
                attempts = 1,
            )
            container.courierTaskDao.upsert(crashSubmitting)
            container.courierTaskDao.upsert(crashFilling)

            val recovered = com.osrs.careerguild.presentation.dashboard.courierguild.CourierStateMachine.recoverOnStartup(
                container.courierTaskDao.getAll()
            )
            recovered.forEach { container.courierTaskDao.upsert(it) }

            assertEquals(
                CourierTaskStatus.UNCONFIRMED.name,
                container.courierTaskDao.getById("crash_submitting_1")?.status,
            )
            val recoveredFilling = container.courierTaskDao.getById("crash_filling_1")
            assertEquals(CourierTaskStatus.QUEUED.name, recoveredFilling?.status)
            assertEquals(2, recoveredFilling?.attempts)
        } finally {
            testDb.close()
            testTailoredDir.deleteRecursively()
            java.io.File(context.filesDir, "generated_resumes").listFiles()?.forEach { pdf ->
                val lower = pdf.name.lowercase()
                if (lower.contains("alex_mercer") || lower.contains("synthetic")) {
                    pdf.delete()
                }
            }
        }
    }
}
