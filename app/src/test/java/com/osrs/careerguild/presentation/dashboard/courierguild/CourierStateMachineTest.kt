package com.osrs.careerguild.presentation.dashboard.courierguild

import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourierStateMachineTest {

    private fun sampleTask(
        status: CourierTaskStatus = CourierTaskStatus.QUEUED,
        attempts: Int = 0,
        snoozedUntil: Long? = null,
    ) = CourierTaskEntity(
        taskId = "task_synthetic_1",
        jobId = "job_synthetic_1",
        jobUrl = "https://boards.greenhouse.io/example/jobs/123",
        ats = "greenhouse",
        title = "Software Engineer",
        company = "ExampleCorp",
        status = status.name,
        attempts = attempts,
        snoozedUntil = snoozedUntil,
    )

    @Test
    fun `happy path transitions from QUEUED to SUBMITTED are valid`() {
        var task = sampleTask(CourierTaskStatus.QUEUED)
        task = CourierStateMachine.transition(task, CourierTaskStatus.FORGING, step = "FORGE_PDF")
        assertEquals(CourierTaskStatus.FORGING.name, task.status)

        task = CourierStateMachine.transition(task, CourierTaskStatus.NAVIGATING, step = "FIND_APPLY")
        assertEquals(CourierTaskStatus.NAVIGATING.name, task.status)

        task = CourierStateMachine.transition(task, CourierTaskStatus.FILLING, step = "UPLOAD_AND_FILL")
        assertEquals(CourierTaskStatus.FILLING.name, task.status)

        task = CourierStateMachine.transition(task, CourierTaskStatus.AWAITING_REVIEW, step = "REVIEW")
        assertEquals(CourierTaskStatus.AWAITING_REVIEW.name, task.status)

        task = CourierStateMachine.transition(task, CourierTaskStatus.SUBMITTING, step = "WRITE_AHEAD_SUBMIT")
        assertEquals(CourierTaskStatus.SUBMITTING.name, task.status)

        task = CourierStateMachine.transition(task, CourierTaskStatus.SUBMITTED, step = "CONFIRMED")
        assertEquals(CourierTaskStatus.SUBMITTED.name, task.status)
    }

    @Test
    fun `BLOCKED_NEEDS_INPUT transitions to QUEUED on resume, SNOOZED on snooze, and CANCELLED on cancel`() {
        val blocked = CourierStateMachine.transition(
            sampleTask(CourierTaskStatus.NAVIGATING),
            CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            blockedReason = CourierBlockedReason.VIDEO_OR_ASSESSMENT,
        )
        assertEquals(CourierTaskStatus.BLOCKED_NEEDS_INPUT.name, blocked.status)
        assertEquals(CourierBlockedReason.VIDEO_OR_ASSESSMENT.name, blocked.blockedReason)

        // Complete -> Resume re-enqueues as QUEUED and clears blockedReason
        val resumed = CourierStateMachine.transition(blocked, CourierTaskStatus.QUEUED)
        assertEquals(CourierTaskStatus.QUEUED.name, resumed.status)
        assertNull(resumed.blockedReason)

        // Snooze -> SNOOZED with snoozedUntil
        val snoozeTarget = 1_800_000_000_000L
        val snoozed = CourierStateMachine.transition(
            blocked,
            CourierTaskStatus.SNOOZED,
            snoozedUntil = snoozeTarget,
        )
        assertEquals(CourierTaskStatus.SNOOZED.name, snoozed.status)
        assertEquals(snoozeTarget, snoozed.snoozedUntil)

        // SNOOZED -> QUEUED when timer fires
        val unsnoozed = CourierStateMachine.transition(snoozed, CourierTaskStatus.QUEUED)
        assertEquals(CourierTaskStatus.QUEUED.name, unsnoozed.status)
        assertNull(unsnoozed.snoozedUntil)

        // Leave it / Cancel -> CANCELLED
        val cancelled = CourierStateMachine.transition(blocked, CourierTaskStatus.CANCELLED)
        assertEquals(CourierTaskStatus.CANCELLED.name, cancelled.status)
        assertTrue(CourierStateMachine.isTerminal(CourierTaskStatus.CANCELLED))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SUBMITTED cannot transition back to QUEUED`() {
        val submitted = sampleTask(CourierTaskStatus.SUBMITTED)
        CourierStateMachine.transition(submitted, CourierTaskStatus.QUEUED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNCONFIRMED cannot auto-retry to QUEUED or SUBMITTING`() {
        val unconfirmed = sampleTask(CourierTaskStatus.UNCONFIRMED)
        assertFalse(CourierStateMachine.canTransition(CourierTaskStatus.UNCONFIRMED, CourierTaskStatus.QUEUED))
        assertFalse(CourierStateMachine.canTransition(CourierTaskStatus.UNCONFIRMED, CourierTaskStatus.SUBMITTING))
        CourierStateMachine.transition(unconfirmed, CourierTaskStatus.QUEUED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `CANCELLED is terminal and cannot transition to QUEUED`() {
        val cancelled = sampleTask(CourierTaskStatus.CANCELLED)
        CourierStateMachine.transition(cancelled, CourierTaskStatus.QUEUED)
    }

    @Test
    fun `startup recovery moves in-flight FORGING, NAVIGATING, FILLING back to QUEUED with attempts + 1 up to max 3`() {
        for (inFlight in listOf(CourierTaskStatus.FORGING, CourierTaskStatus.NAVIGATING, CourierTaskStatus.FILLING)) {
            val attempt0 = sampleTask(status = inFlight, attempts = 0)
            val rec1 = CourierStateMachine.recoverOnStartup(attempt0, maxAttempts = 3)
            assertNotNull(rec1)
            assertEquals(CourierTaskStatus.QUEUED.name, rec1!!.status)
            assertEquals(1, rec1.attempts)

            val attempt1 = sampleTask(status = inFlight, attempts = 1)
            val rec2 = CourierStateMachine.recoverOnStartup(attempt1, maxAttempts = 3)
            assertNotNull(rec2)
            assertEquals(CourierTaskStatus.QUEUED.name, rec2!!.status)
            assertEquals(2, rec2.attempts)

            val attempt2 = sampleTask(status = inFlight, attempts = 2)
            val rec3 = CourierStateMachine.recoverOnStartup(attempt2, maxAttempts = 3)
            assertNotNull(rec3)
            assertEquals(CourierTaskStatus.FAILED.name, rec3!!.status)
            assertEquals(3, rec3.attempts)
        }
    }

    @Test
    fun `startup recovery moves SUBMITTING to UNCONFIRMED and never retries`() {
        val submitting = sampleTask(status = CourierTaskStatus.SUBMITTING, attempts = 0)
        val recovered = CourierStateMachine.recoverOnStartup(submitting)
        assertNotNull(recovered)
        assertEquals(CourierTaskStatus.UNCONFIRMED.name, recovered!!.status)
    }

    @Test
    fun `startup recovery wakes expired SNOOZED task to QUEUED`() {
        val now = 1_700_000_000_000L
        val expiredSnooze = sampleTask(status = CourierTaskStatus.SNOOZED, snoozedUntil = now - 1000L)
        val recovered = CourierStateMachine.recoverOnStartup(expiredSnooze, nowMs = now)
        assertNotNull(recovered)
        assertEquals(CourierTaskStatus.QUEUED.name, recovered!!.status)
        assertNull(recovered.snoozedUntil)

        val futureSnooze = sampleTask(status = CourierTaskStatus.SNOOZED, snoozedUntil = now + 60_000L)
        assertNull(CourierStateMachine.recoverOnStartup(futureSnooze, nowMs = now))
    }
}
