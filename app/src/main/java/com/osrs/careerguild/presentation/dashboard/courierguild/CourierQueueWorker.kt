package com.osrs.careerguild.presentation.dashboard.courierguild

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.osrs.careerguild.CareerGuildApp
import com.osrs.careerguild.core.profile.UserProfileManager
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import com.osrs.careerguild.data.local.entity.toDomain
import com.osrs.careerguild.presentation.MainActivity
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Persistent background WorkManager worker for the Courier auto-apply agent (Phase 1.3 & Reconciliation §2, §3).
 *
 * - Enqueued per task as unique work `"courier-{task_id}"` with [ExistingWorkPolicy.KEEP] and
 *   `NetworkType.CONNECTED` constraint (unless running a local debug fixture).
 * - Promotes to foreground service using channel `"Courier"` and `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
 * - "Pause without dying": on any block (`BLOCKED_NEEDS_INPUT`) or review stop (`AWAITING_REVIEW`),
 *   persists state to Room (`courier_task`), spawns the orange Quest orb (`CourierAutoApplyDispatcher.reportQuest`),
 *   posts a deep-link notification that opens [CourierTakeoverScreen], releases its WebView slot,
 *   and returns `Result.success()`.
 */
class CourierQueueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        val app = applicationContext as? CareerGuildApp
        val container = app?.appContainer ?: return Result.failure()
        val taskDao = container.courierTaskDao
        val webHost = container.courierWebHost

        ensureNotificationChannel(applicationContext)
        runCatching {
            setForeground(createForegroundInfo(taskId))
        }.onFailure {
            Log.w(TAG, "setForeground warning (continuing checkpointed work): ${it.message}")
        }

        if (taskId.isBlank()) {
            // Queue drainer invocation: schedule up to maxConcurrentWorkers runnable tasks
            val runnable = taskDao.getRunnableTasks()
            var delayAccumMs = 0L
            runnable.take(CourierAgentConfig.maxConcurrentWorkers).forEachIndexed { idx, runnableTask ->
                if (idx > 0) {
                    delayAccumMs += Random.nextLong(
                        CourierAgentConfig.interApplicationDelayMinMs,
                        CourierAgentConfig.interApplicationDelayMaxMs + 1,
                    )
                }
                enqueueTask(
                    context = applicationContext,
                    taskId = runnableTask.taskId,
                    initialDelayMs = delayAccumMs,
                    replaceExisting = false,
                )
            }
            return Result.success()
        }

        val initialTask = taskDao.getById(taskId) ?: return Result.success()
        val currentStatus = CourierTaskStatus.fromString(initialTask.status)
        if (CourierStateMachine.isTerminal(currentStatus) || currentStatus == CourierTaskStatus.UNCONFIRMED) {
            return Result.success()
        }

        // Daily cap check (default 15 applications submitted per rolling 24h)
        val twentyFourHoursAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val submittedToday = taskDao.countSubmittedSince(twentyFourHoursAgo)
        if (submittedToday >= CourierAgentConfig.dailyCap &&
            SubmitPolicy.fromString(initialTask.submitPolicy) == SubmitPolicy.AUTO_SUBMIT
        ) {
            Log.i(TAG, "Daily cap (${CourierAgentConfig.dailyCap}) reached; task $taskId will stop at AWAITING_REVIEW.")
        }

        val slotIndex = webHost.acquireSlot(initialTask)
        if (slotIndex == null) {
            Log.i(TAG, "All ${webHost.effectiveMaxSlots} WebView worker slots busy; retrying $taskId later.")
            return Result.retry()
        }

        return try {
            runCheckpointedTask(
                initialTask = initialTask,
                slotIndex = slotIndex,
                container = container,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error executing Courier task $taskId: ${t.message}", t)
            val latest = taskDao.getById(taskId) ?: initialTask
            val latestStatus = CourierTaskStatus.fromString(latest.status)
            if (latestStatus == CourierTaskStatus.SUBMITTING) {
                val unconfirmed = CourierStateMachine.recoverOnStartup(latest) ?: latest.copy(
                    status = CourierTaskStatus.UNCONFIRMED.name,
                    lastError = t.message,
                )
                taskDao.upsert(unconfirmed)
                syncDispatcherStatus(unconfirmed)
            } else {
                val recovered = CourierStateMachine.recoverOnStartup(latest)
                if (recovered != null) {
                    taskDao.upsert(recovered)
                    syncDispatcherStatus(recovered)
                }
            }
            Result.success()
        } finally {
            webHost.releaseSlot(taskId)
        }
    }

    private suspend fun runCheckpointedTask(
        initialTask: CourierTaskEntity,
        slotIndex: Int,
        container: com.osrs.careerguild.di.AppContainer,
    ): Result {
        val taskDao = container.courierTaskDao
        val webHost = container.courierWebHost
        var task = initialTask
        val filledFields = parseFilledFieldsMap(task.filledFieldsJson)

        // -------------------------------------------------------------------
        // STEP 1: FORGING (Phase 2.1 & Reconciliation §7)
        // -------------------------------------------------------------------
        task = transitionAndPersist(
            task = task,
            target = CourierTaskStatus.FORGING,
            step = "FORGE_AND_VERIFY_RESUME",
            filledFields = filledFields,
            container = container,
        )

        val forgeResult = CourierPipelineExecutor.ensureVerifiedForgeResume(
            context = applicationContext,
            task = task,
            container = container,
        )
        if (forgeResult.pdfFile == null) {
            return pauseBlocked(
                task = task,
                slotIndex = slotIndex,
                reason = CourierBlockedReason.RESUME_VERIFICATION_FAILED,
                blockedFields = forgeResult.violations.ifEmpty { listOf("Resume verification failed twice") },
                errorMsg = forgeResult.errorMessage ?: "Resume verification failed",
                container = container,
            )
        }

        task = task.copy(
            resumePdfPath = forgeResult.pdfFile.absolutePath,
            updatedAt = System.currentTimeMillis(),
        )
        taskDao.upsert(task)

        if (isStopped) return Result.success()

        // -------------------------------------------------------------------
        // STEP 2: NAVIGATING (Phase 2.2 & Phase 2.3)
        // -------------------------------------------------------------------
        val targetStartUrl = task.currentUrl.ifBlank { task.jobUrl }
        val atsLower = CourierAgentConfig.detectAtsPlatform(targetStartUrl, task.ats).lowercase()
        if (atsLower.contains("workday") || atsLower.contains("linkedin")) {
            return pauseBlocked(
                task = task,
                slotIndex = slotIndex,
                reason = CourierBlockedReason.UNSUPPORTED_ATS,
                blockedFields = listOf("Unsupported ATS (${task.ats})"),
                errorMsg = "Unsupported ATS platform requires manual takeover",
                container = container,
            )
        }

        task = transitionAndPersist(
            task = task,
            target = CourierTaskStatus.NAVIGATING,
            step = "LOAD_PORTAL",
            currentUrl = targetStartUrl,
            filledFields = filledFields,
            container = container,
        )

        val loadedUrl = webHost.loadUrlAndWait(task.taskId, targetStartUrl) ?: targetStartUrl
        if (webHost.wasRendererCrashed(task.taskId)) {
            return Result.success()
        }
        val httpError = webHost.getLastHttpError(task.taskId)
        if (httpError != null && httpError >= 500) {
            return pauseBlocked(
                task = task.copy(currentUrl = loadedUrl),
                slotIndex = slotIndex,
                reason = CourierBlockedReason.SITE_ERROR,
                blockedFields = listOf("HTTP $httpError"),
                errorMsg = "Portal returned HTTP $httpError",
                container = container,
            )
        }

        task = webHost.checkpointStep(
            task = task,
            status = CourierTaskStatus.NAVIGATING,
            step = "PORTAL_LOADED",
            currentUrl = loadedUrl,
            filledFields = filledFields,
        )

        // Check early guardrails (CAPTCHA, Video/Assessment, Login/MFA)
        val earlyBlock = CourierPipelineExecutor.checkEarlyBlocksAndLogin(
            task = task,
            webHost = webHost,
            container = container,
        )
        if (earlyBlock != null) {
            return pauseBlocked(
                task = task.copy(currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { loadedUrl }),
                slotIndex = slotIndex,
                reason = earlyBlock.reason,
                blockedFields = earlyBlock.blockedFields,
                errorMsg = earlyBlock.detail,
                container = container,
            )
        }

        // Navigate to application form (dismiss cookie banner, click Apply trigger if not already on form)
        val navOutcome = CourierPipelineExecutor.navigateToApplicationForm(
            task = task,
            webHost = webHost,
            courierModel = container.courierModel,
        )
        if (!navOutcome.formReached) {
            return pauseBlocked(
                task = task.copy(currentUrl = navOutcome.currentUrl),
                slotIndex = slotIndex,
                reason = navOutcome.blockedReason ?: CourierBlockedReason.NO_APPLY_PATH,
                blockedFields = listOf("Apply button / form not found after 2 attempts"),
                errorMsg = navOutcome.detail,
                container = container,
            )
        }

        // Re-check guardrails after navigating into the form
        val postNavBlock = CourierPipelineExecutor.checkEarlyBlocksAndLogin(
            task = task.copy(currentUrl = navOutcome.currentUrl),
            webHost = webHost,
            container = container,
        )
        if (postNavBlock != null) {
            return pauseBlocked(
                task = task.copy(currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { navOutcome.currentUrl }),
                slotIndex = slotIndex,
                reason = postNavBlock.reason,
                blockedFields = postNavBlock.blockedFields,
                errorMsg = postNavBlock.detail,
                container = container,
            )
        }

        if (isStopped) return Result.success()

        // -------------------------------------------------------------------
        // STEP 3: FILLING — Upload First, Overwrite Autofill, EEO & Grounded Answers (Phases 2.4–3.6)
        // -------------------------------------------------------------------
        val formUrl = webHost.getCurrentUrl(task.taskId).ifBlank { navOutcome.currentUrl }
        task = transitionAndPersist(
            task = task,
            target = CourierTaskStatus.FILLING,
            step = "UPLOAD_RESUME_FIRST",
            currentUrl = formUrl,
            filledFields = filledFields,
            container = container,
        )

        val fillOutcome = CourierPipelineExecutor.uploadAndFillForm(
            context = applicationContext,
            task = task,
            forgePdfFile = forgeResult.pdfFile,
            tailoredResume = forgeResult.tailoredResume,
            filledFields = filledFields,
            webHost = webHost,
            container = container,
        )

        task = task.copy(
            currentUrl = fillOutcome.currentUrl,
            filledFieldsJson = JSONObject(filledFields).toString(),
            reviewRequired = task.reviewRequired || fillOutcome.reviewRequired,
            updatedAt = System.currentTimeMillis(),
        )
        taskDao.upsert(task)

        if (fillOutcome.blockedReason != null) {
            return pauseBlocked(
                task = task,
                slotIndex = slotIndex,
                reason = fillOutcome.blockedReason,
                blockedFields = fillOutcome.blockedFields,
                errorMsg = fillOutcome.detail,
                container = container,
            )
        }

        // -------------------------------------------------------------------
        // STEP 4: SUBMIT POLICY & WRITE-AHEAD SUBMISSION (Phase 4.1 & 4.2)
        // -------------------------------------------------------------------
        val policy = SubmitPolicy.fromString(task.submitPolicy)
        val twentyFourHoursAgoNow = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val submittedCountNow = taskDao.countSubmittedSince(twentyFourHoursAgoNow)
        val canAutoSubmit = policy == SubmitPolicy.AUTO_SUBMIT &&
            !task.reviewRequired &&
            !fillOutcome.reviewRequired &&
            fillOutcome.allRequiredMatched &&
            fillOutcome.isSupportedAtsAdapter &&
            submittedCountNow < CourierAgentConfig.dailyCap &&
            fillOutcome.submitElementId != null

        if (!canAutoSubmit) {
            task = transitionAndPersist(
                task = task,
                target = CourierTaskStatus.AWAITING_REVIEW,
                step = "READY_FOR_USER_REVIEW",
                currentUrl = fillOutcome.currentUrl,
                filledFields = filledFields,
                container = container,
            )
            postReviewOrBlockedNotification(
                context = applicationContext,
                task = task,
                title = "Ready to review: ${task.company}",
                body = "${task.title} form is filled and ready for your review.",
            )
            return Result.success()
        }

        // Write-ahead rule: set status SUBMITTING in Room BEFORE clicking submit
        task = transitionAndPersist(
            task = task,
            target = CourierTaskStatus.SUBMITTING,
            step = "WRITE_AHEAD_SUBMIT",
            currentUrl = fillOutcome.currentUrl,
            filledFields = filledFields,
            container = container,
        )

        val confirmed = CourierPipelineExecutor.clickSubmitAndVerifyConfirmation(
            task = task,
            submitElementId = fillOutcome.submitElementId!!,
            webHost = webHost,
        )

        if (confirmed) {
            task = transitionAndPersist(
                task = task,
                target = CourierTaskStatus.SUBMITTED,
                step = "SUBMIT_CONFIRMED",
                currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { task.currentUrl },
                filledFields = filledFields,
                container = container,
            )
            container.markAppliedUseCase(task.jobId)
        } else {
            task = transitionAndPersist(
                task = task,
                target = CourierTaskStatus.UNCONFIRMED,
                step = "SUBMIT_UNCONFIRMED",
                currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { task.currentUrl },
                filledFields = filledFields,
                container = container,
            )
        }

        return Result.success()
    }

    private suspend fun transitionAndPersist(
        task: CourierTaskEntity,
        target: CourierTaskStatus,
        step: String,
        currentUrl: String = task.currentUrl,
        filledFields: Map<String, String>,
        container: com.osrs.careerguild.di.AppContainer,
    ): CourierTaskEntity {
        val transitioned = CourierStateMachine.transition(
            task = task,
            targetStatus = target,
            step = step,
            currentUrl = currentUrl,
        ).copy(
            filledFieldsJson = JSONObject(filledFields).toString(),
        )
        container.courierTaskDao.upsert(transitioned)
        container.courierWebHost.checkpointStep(
            task = transitioned,
            status = target,
            step = step,
            currentUrl = currentUrl,
            filledFields = filledFields,
        )
        syncDispatcherStatus(transitioned)
        Log.i(TAG, "Task ${task.taskId} [${task.company}] -> ${target.name} (step=$step)")
        return transitioned
    }

    private suspend fun pauseBlocked(
        task: CourierTaskEntity,
        slotIndex: Int,
        reason: CourierBlockedReason,
        blockedFields: List<String>,
        errorMsg: String,
        container: com.osrs.careerguild.di.AppContainer,
    ): Result {
        val blockedJson = encodeBlockedFieldsJson(blockedFields)
        val blockedTask = CourierStateMachine.transition(
            task = task,
            targetStatus = CourierTaskStatus.BLOCKED_NEEDS_INPUT,
            step = "BLOCKED_${reason.name}",
            currentUrl = task.currentUrl.ifBlank { task.jobUrl },
            blockedReason = reason,
            blockedFieldsJson = blockedJson,
            lastError = errorMsg,
        )
        container.courierTaskDao.upsert(blockedTask)
        syncDispatcherStatus(blockedTask)

        // Reconciliation §3: Every BLOCKED_NEEDS_INPUT triggers the existing orange Quest orb
        CourierAutoApplyDispatcher.reportQuest(
            CourierCaptchaQuest(
                company = blockedTask.company.ifBlank { "Portal" },
                title = blockedTask.title,
                kind = reason.name,
                awaitingHuman = true,
                workerSlotIndex = slotIndex,
                appId = blockedTask.taskId,
            )
        )

        CourierGuardrails.notifyHandoff(
            context = applicationContext,
            company = blockedTask.company.ifBlank { "Courier" },
            title = blockedTask.title,
            reason = "${reason.name}: $errorMsg",
        )
        postReviewOrBlockedNotification(
            context = applicationContext,
            task = blockedTask,
            title = "Courier needs input: ${blockedTask.company}",
            body = "${reason.name} — tap to open Takeover.",
        )

        Log.i(TAG, "Task ${blockedTask.taskId} paused with BLOCKED_NEEDS_INPUT ($reason): $errorMsg")
        return Result.success()
    }

    private fun syncDispatcherStatus(task: CourierTaskEntity) {
        val status = CourierTaskStatus.fromString(task.status)
        val uiStatus = CourierStateMachine.toUiApplicationStatus(status)
        CourierAutoApplyDispatcher.updateApplicationStatus(task.jobId, uiStatus)
        CourierAutoApplyDispatcher.updateApplicationStatus(task.taskId, uiStatus)
    }

    private fun createForegroundInfo(taskId: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Courier Guild Agent Active")
            .setContentText(if (taskId.isNotBlank()) "Processing task $taskId" else "Draining application queue")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG = "CourierWorker"
        const val CHANNEL_ID = "Courier"
        const val KEY_TASK_ID = "task_id"
        const val EXTRA_TAKEOVER_TASK_ID = "extra_courier_takeover_task_id"
        private const val FOREGROUND_NOTIFICATION_ID = 7301

        fun uniqueWorkName(taskId: String): String = "courier-$taskId"

        fun ensureNotificationChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Courier Background Agent",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Persistent Courier auto-apply background tasks and review alerts"
                    }
                )
            }
        }

        @SuppressLint("MissingPermission")
        fun postReviewOrBlockedNotification(
            context: Context,
            task: CourierTaskEntity,
            title: String,
            body: String,
        ) {
            if (!CourierGuardrails.canPostNotifications(context)) return
            ensureNotificationChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TAKEOVER_TASK_ID, task.taskId)
            }
            val pi = PendingIntent.getActivity(
                context,
                task.taskId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            runCatching {
                NotificationManagerCompat.from(context).notify(task.taskId.hashCode(), notification)
            }
        }

        /**
         * Enqueues unique background work `"courier-{taskId}"` with [ExistingWorkPolicy.KEEP]
         * (or [ExistingWorkPolicy.REPLACE] when explicitly resuming/snoozing).
         */
        fun enqueueTask(
            context: Context,
            taskId: String,
            initialDelayMs: Long = 0L,
            requireNetwork: Boolean = true,
            replaceExisting: Boolean = false,
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (requireNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                .build()

            val requestBuilder = OneTimeWorkRequestBuilder<CourierQueueWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TASK_ID to taskId))

            if (initialDelayMs > 0L) {
                requestBuilder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            }

            val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(uniqueWorkName(taskId), policy, requestBuilder.build())
        }

        fun cancelTaskWork(context: Context, taskId: String) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(taskId))
        }

        fun enqueueAvailableSlots(context: Context) {
            val request = OneTimeWorkRequestBuilder<CourierQueueWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("courier-queue-drainer", ExistingWorkPolicy.KEEP, request)
        }

        internal fun parseFilledFieldsMap(rawJson: String): MutableMap<String, String> {
            val map = mutableMapOf<String, String>()
            if (rawJson.isBlank()) return map
            runCatching {
                val obj = JSONObject(rawJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.optString(k, "")
                }
            }
            return map
        }
    }
}
