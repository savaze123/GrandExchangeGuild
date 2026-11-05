package com.osrs.careerguild.core.forge

import com.osrs.careerguild.domain.resume.TailoredResume
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ForgeRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    @Test
    fun forgeTask_roundTrip_preservesAllFields() {
        val task = ForgeTask(
            schema = ForgeTask.SCHEMA_V1,
            taskId = "task_stripe_101",
            jobId = "job_101",
            jobUrl = "https://boards.greenhouse.io/stripe/jobs/101",
            ats = "greenhouse",
            title = "Staff Infrastructure Engineer",
            company = "Stripe",
            autoSubmitApproved = true,
            resumePdf = "resume.pdf",
            fields = ForgeTaskFields(
                firstName = "Alex",
                lastName = "Mercer",
                email = "alex@example.com",
                phone = "555-0199",
                location = "San Francisco, CA",
                linkedinUrl = "https://linkedin.com/in/alex",
                githubUrl = "https://github.com/alex",
                website = null,
            ),
            answers = listOf(
                ForgeAnswer(questionPattern = "work_authorization", answer = "Yes"),
                ForgeAnswer(questionPattern = "sponsorship_needed", answer = "No"),
            ),
            tailoredResume = TailoredResume(
                summary = "Experienced distributed systems engineer",
                skills = listOf("Kotlin", "Go", "Distributed Systems"),
                isTailored = true,
                targetHeadline = "Staff Infrastructure Engineer | Kotlin",
            ),
            createdAt = 1700000000000L,
        )

        val jsonString = strictJson.encodeToString(task)
        val decoded = strictJson.decodeFromString<ForgeTask>(jsonString)

        assertEquals("forge_task.v1", decoded.schema)
        assertEquals("task_stripe_101", decoded.taskId)
        assertEquals("job_101", decoded.jobId)
        assertEquals("https://boards.greenhouse.io/stripe/jobs/101", decoded.jobUrl)
        assertEquals("greenhouse", decoded.ats)
        assertEquals("Staff Infrastructure Engineer", decoded.title)
        assertEquals("Stripe", decoded.company)
        assertTrue(decoded.autoSubmitApproved)
        assertEquals("resume.pdf", decoded.resumePdf)
        assertEquals("Alex", decoded.fields.firstName)
        assertEquals("Mercer", decoded.fields.lastName)
        assertEquals("alex@example.com", decoded.fields.email)
        assertNull(decoded.fields.website)
        assertEquals(2, decoded.answers.size)
        assertEquals("work_authorization", decoded.answers[0].questionPattern)
        assertEquals("Yes", decoded.answers[0].answer)
        assertEquals("Staff Infrastructure Engineer | Kotlin", decoded.tailoredResume.targetHeadline)
        assertEquals(1700000000000L, decoded.createdAt)
    }

    @Test
    fun forgeResult_roundTrip_allStatuses() {
        val statuses = listOf(
            ForgeResultStatus.FILLED_AWAITING_REVIEW,
            ForgeResultStatus.SUBMITTED,
            ForgeResultStatus.NEEDS_INPUT,
            ForgeResultStatus.FAILED,
            ForgeResultStatus.SUBMIT_UNCONFIRMED,
        )

        for (st in statuses) {
            val result = ForgeResult(
                schema = ForgeResult.SCHEMA_V1,
                taskId = "task_test_${st.name}",
                status = st,
                missingFields = if (st == ForgeResultStatus.NEEDS_INPUT) listOf("clearance_level") else emptyList(),
                filledFieldNames = listOf("first_name", "email", "resume"),
                confirmationText = if (st == ForgeResultStatus.SUBMITTED) "Application submitted successfully" else null,
                screenshot = if (st == ForgeResultStatus.FAILED) "screenshots/err.png" else null,
                finishedAt = 1700000005000L,
            )

            val jsonString = strictJson.encodeToString(result)
            val decoded = strictJson.decodeFromString<ForgeResult>(jsonString)

            assertEquals(ForgeResult.SCHEMA_V1, decoded.schema)
            assertEquals("task_test_${st.name}", decoded.taskId)
            assertEquals(st, decoded.status)
            assertEquals(result.missingFields, decoded.missingFields)
            assertEquals(listOf("first_name", "email", "resume"), decoded.filledFieldNames)
            assertEquals(result.confirmationText, decoded.confirmationText)
            assertEquals(result.screenshot, decoded.screenshot)
        }
    }

    @Test
    fun forgeResult_strictDecoding_rejectsUnknownKeys() {
        val malformedJson = """
            {
                "schema": "forge_result.v1",
                "task_id": "task_1",
                "status": "submitted",
                "missing_fields": [],
                "filled_field_names": ["first_name"],
                "unexpected_rogue_key": "hacker_payload",
                "finished_at": 1700000000000
            }
        """.trimIndent()

        try {
            strictJson.decodeFromString<ForgeResult>(malformedJson)
            fail("Expected SerializationException due to strict unknown keys check")
        } catch (e: SerializationException) {
            assertTrue(e.message?.contains("unexpected_rogue_key") == true)
        }
    }

    @Test
    fun forgeResult_strictDecoding_rejectsInvalidStatus() {
        val badStatusJson = """
            {
                "schema": "forge_result.v1",
                "task_id": "task_1",
                "status": "some_unsupported_status",
                "missing_fields": [],
                "filled_field_names": [],
                "finished_at": 1700000000000
            }
        """.trimIndent()

        try {
            strictJson.decodeFromString<ForgeResult>(badStatusJson)
            fail("Expected SerializationException due to invalid status")
        } catch (e: SerializationException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun forgeInbox_filtersUnknownTasksAndWrongSchema() = runBlocking {
        val inboxDir = tempFolder.newFolder("forge_inbox")
        val inbox = ForgeInbox(
            json = strictJson,
            baseDirOverride = inboxDir,
        )

        // 1. Valid result with known task_id
        val validFile = File(inboxDir, "result_stripe.json")
        validFile.writeText(
            """
            {
                "schema": "forge_result.v1",
                "task_id": "task_known_stripe",
                "status": "submitted",
                "missing_fields": [],
                "filled_field_names": ["first_name", "email"],
                "confirmation_text": "Thank you for applying",
                "finished_at": 1700000000000
            }
            """.trimIndent(),
            Charsets.UTF_8
        )

        // 2. Unknown task_id
        val unknownFile = File(inboxDir, "result_unknown.json")
        unknownFile.writeText(
            """
            {
                "schema": "forge_result.v1",
                "task_id": "task_stranger",
                "status": "submitted",
                "missing_fields": [],
                "filled_field_names": [],
                "finished_at": 1700000000000
            }
            """.trimIndent(),
            Charsets.UTF_8
        )

        // 3. Wrong schema
        val badSchemaFile = File(inboxDir, "result_bad_schema.json")
        badSchemaFile.writeText(
            """
            {
                "schema": "forge_result.v999",
                "task_id": "task_known_stripe",
                "status": "submitted",
                "missing_fields": [],
                "filled_field_names": [],
                "finished_at": 1700000000000
            }
            """.trimIndent(),
            Charsets.UTF_8
        )

        // 4. Corrupted / unparseable json
        val corruptedFile = File(inboxDir, "result_corrupted.json")
        corruptedFile.writeText("{ this is invalid json }", Charsets.UTF_8)

        val known = setOf("task_known_stripe")
        val results = inbox.readResults(known)

        assertEquals(1, results.size)
        assertEquals("task_known_stripe", results[0].result.taskId)
        assertEquals(ForgeResultStatus.SUBMITTED, results[0].result.status)
        assertEquals("Thank you for applying", results[0].result.confirmationText)

        // Test delete
        val deleted = inbox.deleteResultFile(results[0].resultFile)
        assertTrue(deleted)
        assertFalse(validFile.exists())
    }

    @Test
    fun forgeOutbox_writesTaskAndCopiesPdf() = runBlocking {
        val outboxDir = tempFolder.newFolder("forge_outbox")
        val outbox = ForgeOutbox(
            baseDirOverride = outboxDir,
        )

        val dummyPdf = tempFolder.newFile("sample_source.pdf")
        dummyPdf.writeText("%PDF-1.4 DUMMY PDF CONTENT", Charsets.UTF_8)

        val task = ForgeTask(
            taskId = "task_outbox_test",
            jobId = "job_99",
            jobUrl = "https://jobs.lever.co/test/99",
            ats = "lever",
            title = "Mobile Lead",
            company = "TestCorp",
            autoSubmitApproved = false,
            resumePdf = "resume.pdf",
            fields = ForgeTaskFields(firstName = "Synthetic", lastName = "Candidate", email = "syn@test.com"),
            tailoredResume = TailoredResume(summary = "Lead Engineer", skills = listOf("Kotlin")),
        )

        val writtenTaskFile = outbox.writeTask(task, dummyPdf)
        assertTrue(writtenTaskFile.exists())
        assertEquals("task.json", writtenTaskFile.name)

        val taskDir = writtenTaskFile.parentFile
        assertNotNull(taskDir)
        val copiedPdf = File(taskDir!!, "resume.pdf")
        assertTrue(copiedPdf.exists())
        assertTrue(copiedPdf.readText().contains("%PDF-1.4"))

        val pending = outbox.listPendingTaskIds()
        assertTrue(pending.contains("task_outbox_test"))

        val folderDeleted = outbox.deleteTaskFolder("task_outbox_test")
        assertTrue(folderDeleted)
        assertFalse(taskDir.exists())
    }
}
