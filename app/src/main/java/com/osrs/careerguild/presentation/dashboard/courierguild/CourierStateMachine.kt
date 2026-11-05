package com.osrs.careerguild.presentation.dashboard.courierguild

import com.osrs.careerguild.data.local.entity.CourierTaskEntity

/**
 * Canonical statuses for the persistent Courier background worker (Phase 1.2 & Reconciliation §3).
 */
enum class CourierTaskStatus {
    QUEUED,
    FORGING,
    NAVIGATING,
    FILLING,
    AWAITING_REVIEW,
    SUBMITTING,
    SUBMITTED,
    BLOCKED_NEEDS_INPUT,
    SNOOZED,
    CANCELLED,
    FAILED,
    UNCONFIRMED;

    companion object {
        fun fromString(raw: String?): CourierTaskStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: QUEUED
    }
}

/**
 * Canonical blocked reasons for `BLOCKED_NEEDS_INPUT` (Phases 2–4 & Reconciliation §3).
 */
enum class CourierBlockedReason {
    RESUME_VERIFICATION_FAILED,
    NO_APPLY_PATH,
    LOGIN_REQUIRED,
    CAPTCHA_OR_CHALLENGE,
    VIDEO_OR_ASSESSMENT,
    UNSUPPORTED_ATS,
    SITE_ERROR,
    MISSING_GROUNDING,
    MISSING_REQUIRED_FIELDS,
    DAILY_CAP_REACHED;

    companion object {
        fun fromString(raw: String?): CourierBlockedReason? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Submit policy governing whether the worker stops at `AWAITING_REVIEW` or proceeds to `SUBMITTING` (Phase 4.1).
 */
enum class SubmitPolicy {
    REVIEW_BEFORE_SUBMIT,
    AUTO_SUBMIT;

    companion object {
        fun fromString(raw: String?): SubmitPolicy =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: REVIEW_BEFORE_SUBMIT
    }
}

/**
 * Pure Kotlin state machine for the persistent Courier background agent.
 *
 * Invariants:
 * 1. Validates every status transition against an explicit allowlist.
 * 2. Write-ahead rule: `SUBMITTING` must be persisted before clicking submit.
 *    If a crash or process death occurs while `SUBMITTING`, startup recovery transitions the task
 *    to `UNCONFIRMED` and NEVER auto-retries it.
 * 3. In-flight tasks (`FORGING`, `NAVIGATING`, `FILLING`) found on startup or after renderer death
 *    transition back to `QUEUED` with `attempts + 1` (up to [MAX_ATTEMPTS] = 3, then `FAILED`).
 * 4. Supports `SNOOZED` (with `snoozed_until` delay) and `CANCELLED` (immediate termination).
 */
object CourierStateMachine {

    const val MAX_ATTEMPTS = 3
    const val DEFAULT_SNOOZE_DURATION_MS = 30 * 60 * 1000L // 30 minutes default snooze

    private val IN_FLIGHT_STATUSES = setOf(
        CourierTaskStatus.FORGING,
        CourierTaskStatus.NAVIGATING,
        CourierTaskStatus.FILLING,
    )

    private val ALLOWED_TRANSITIONS: Map<CourierTaskStatus, Set<CourierTaskStatus>> = mapOf(
        CourierTaskStatus.QUEUED to setOf(
            CourierTaskStatus.FORGING,
            CourierTaskStatus.NAVIGATING,
            CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            CourierTaskStatus.SNOOZED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.FORGING to setOf(
            CourierTaskStatus.NAVIGATING,
            CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            CourierTaskStatus.QUEUED,
            CourierTaskStatus.SNOOZED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.NAVIGATING to setOf(
            CourierTaskStatus.FILLING,
            CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            CourierTaskStatus.QUEUED,
            CourierTaskStatus.SNOOZED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.FILLING to setOf(
            CourierTaskStatus.AWAITING_REVIEW,
            CourierTaskStatus.SUBMITTING,
            CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            CourierTaskStatus.QUEUED,
            CourierTaskStatus.SNOOZED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.AWAITING_REVIEW to setOf(
            CourierTaskStatus.SUBMITTING,
            CourierTaskStatus.SUBMITTED,
            CourierTaskStatus.QUEUED,
            CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            CourierTaskStatus.SNOOZED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.BLOCKED_NEEDS_INPUT to setOf(
            CourierTaskStatus.QUEUED,
            CourierTaskStatus.SUBMITTED,
            CourierTaskStatus.SNOOZED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.SNOOZED to setOf(
            CourierTaskStatus.QUEUED,
            CourierTaskStatus.CANCELLED,
            CourierTaskStatus.FAILED,
        ),
        CourierTaskStatus.SUBMITTING to setOf(
            CourierTaskStatus.SUBMITTED,
            CourierTaskStatus.UNCONFIRMED,
            CourierTaskStatus.FAILED,
        ),
        // UNCONFIRMED is never auto-retried; only manual user confirmation in Takeover can mark it SUBMITTED or CANCELLED.
        CourierTaskStatus.UNCONFIRMED to setOf(
            CourierTaskStatus.SUBMITTED,
            CourierTaskStatus.CANCELLED,
        ),
        CourierTaskStatus.SUBMITTED to emptySet(),
        CourierTaskStatus.CANCELLED to emptySet(),
        CourierTaskStatus.FAILED to emptySet(),
    )

    fun isInFlight(status: CourierTaskStatus): Boolean = status in IN_FLIGHT_STATUSES

    fun isTerminal(status: CourierTaskStatus): Boolean =
        status == CourierTaskStatus.SUBMITTED ||
            status == CourierTaskStatus.CANCELLED ||
            status == CourierTaskStatus.FAILED

    /**
     * Returns true if a task with [status] is considered open or already submitted for duplicate-guard purposes.
     */
    fun isOpenOrSubmitted(status: CourierTaskStatus): Boolean =
        status != CourierTaskStatus.CANCELLED && status != CourierTaskStatus.FAILED

    fun canTransition(from: CourierTaskStatus, to: CourierTaskStatus): Boolean {
        if (from == to) return true
        return ALLOWED_TRANSITIONS[from]?.contains(to) == true
    }

    fun requireTransition(from: CourierTaskStatus, to: CourierTaskStatus): CourierTaskStatus {
        require(canTransition(from, to)) {
            "Illegal CourierStateMachine transition: $from -> $to"
        }
        return to
    }

    /**
     * Transitions [task] to [targetStatus], throwing [IllegalArgumentException] if the transition is disallowed.
     */
    fun transition(
        task: CourierTaskEntity,
        targetStatus: CourierTaskStatus,
        step: String = task.step,
        currentUrl: String = task.currentUrl,
        blockedReason: CourierBlockedReason? = null,
        blockedFieldsJson: String = task.blockedFieldsJson,
        lastError: String? = task.lastError,
        snoozedUntil: Long? = if (targetStatus == CourierTaskStatus.SNOOZED) task.snoozedUntil else null,
        nowMs: Long = System.currentTimeMillis(),
    ): CourierTaskEntity {
        val currentStatus = CourierTaskStatus.fromString(task.status)
        requireTransition(currentStatus, targetStatus)
        return task.copy(
            status = targetStatus.name,
            step = step,
            currentUrl = currentUrl,
            blockedReason = if (targetStatus == CourierTaskStatus.BLOCKED_NEEDS_INPUT) {
                blockedReason?.name ?: task.blockedReason
            } else {
                null
            },
            blockedFieldsJson = if (targetStatus == CourierTaskStatus.BLOCKED_NEEDS_INPUT) {
                blockedFieldsJson
            } else {
                "[]"
            },
            lastError = lastError,
            snoozedUntil = snoozedUntil,
            updatedAt = nowMs,
        )
    }

    /**
     * Recovers a single task on app startup or after renderer process crash:
     * - `FORGING`, `NAVIGATING`, `FILLING` -> `QUEUED` with `attempts + 1` (if `attempts + 1 >= maxAttempts`, -> `FAILED`).
     * - `SUBMITTING` -> `UNCONFIRMED` (write-ahead rule: never auto-retried).
     * - `SNOOZED` whose `snoozedUntil <= nowMs` -> `QUEUED`.
     * - All other statuses remain unchanged (returns null if no change needed).
     */
    fun recoverOnStartup(
        task: CourierTaskEntity,
        maxAttempts: Int = MAX_ATTEMPTS,
        nowMs: Long = System.currentTimeMillis(),
    ): CourierTaskEntity? {
        val current = CourierTaskStatus.fromString(task.status)
        return when {
            current == CourierTaskStatus.SUBMITTING -> {
                task.copy(
                    status = CourierTaskStatus.UNCONFIRMED.name,
                    step = "CRASH_DURING_SUBMIT",
                    lastError = "Write-ahead guard: task was in SUBMITTING during crash/restart; moved to UNCONFIRMED and never auto-retried.",
                    updatedAt = nowMs,
                )
            }
            current in IN_FLIGHT_STATUSES -> {
                val nextAttempts = task.attempts + 1
                if (nextAttempts >= maxAttempts) {
                    task.copy(
                        status = CourierTaskStatus.FAILED.name,
                        attempts = nextAttempts,
                        lastError = "Exceeded max retry attempts ($maxAttempts) after interrupted ${current.name} state.",
                        updatedAt = nowMs,
                    )
                } else {
                    task.copy(
                        status = CourierTaskStatus.QUEUED.name,
                        attempts = nextAttempts,
                        lastError = "Recovered from interrupted ${current.name} (attempt $nextAttempts/$maxAttempts).",
                        updatedAt = nowMs,
                    )
                }
            }
            current == CourierTaskStatus.SNOOZED && task.snoozedUntil != null && task.snoozedUntil <= nowMs -> {
                task.copy(
                    status = CourierTaskStatus.QUEUED.name,
                    snoozedUntil = null,
                    updatedAt = nowMs,
                )
            }
            else -> null
        }
    }

    /**
     * Batch overload recovering a list of tasks on app startup. Returns only the modified tasks.
     */
    fun recoverOnStartup(
        tasks: List<CourierTaskEntity>,
        maxAttempts: Int = MAX_ATTEMPTS,
        nowMs: Long = System.currentTimeMillis(),
    ): List<CourierTaskEntity> =
        tasks.mapNotNull { recoverOnStartup(it, maxAttempts, nowMs) }

    /**
     * Maps [CourierTaskStatus] to the UI-facing [ApplicationStatus] for backward compatibility
     * with existing ledger and Agent Room UI consumers.
     */
    fun toUiApplicationStatus(status: CourierTaskStatus): ApplicationStatus = when (status) {
        CourierTaskStatus.QUEUED, CourierTaskStatus.SNOOZED -> ApplicationStatus.QUEUED
        CourierTaskStatus.FORGING, CourierTaskStatus.NAVIGATING, CourierTaskStatus.FILLING, CourierTaskStatus.SUBMITTING ->
            ApplicationStatus.IN_TRANSIT
        CourierTaskStatus.AWAITING_REVIEW -> ApplicationStatus.AWAITING_REVIEW
        CourierTaskStatus.BLOCKED_NEEDS_INPUT -> ApplicationStatus.NEEDS_INPUT
        CourierTaskStatus.SUBMITTED -> ApplicationStatus.SUBMITTED
        CourierTaskStatus.FAILED, CourierTaskStatus.CANCELLED -> ApplicationStatus.FAILED
        CourierTaskStatus.UNCONFIRMED -> ApplicationStatus.UNCONFIRMED
    }
}
