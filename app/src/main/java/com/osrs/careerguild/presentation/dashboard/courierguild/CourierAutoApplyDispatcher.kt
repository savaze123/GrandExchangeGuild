package com.osrs.careerguild.presentation.dashboard.courierguild

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.WorkManager
import com.osrs.careerguild.domain.model.JobListing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Central dispatcher and security guard for the Courier Guild Auto-Apply Agent.
 *
 * Implements:
 * 1. LinkedIn Anti-Ban Platform Exclusion:
 *    Strictly excludes any listing whose `source` or `applicationUrl` (`atsUrl`/`applyUrl`)
 *    contains "LinkedIn" (case-insensitive) from automated WebView/DOM pipeline execution,
 *    routing users to an external browser tab as "Manual Apply Only".
 * 2. Credential Generation & Local Vault Cipher for 'Accounts Made by Courier'.
 * 3. instruction.md §5 Concurrency Limit: Enforces strict 5 concurrent job applications.
 * 4. instruction.md §6 Central Quest Notification tracking.
 */
object CourierAutoApplyDispatcher {

    /** Maximum concurrent active worker applications allowed simultaneously (instruction.md §5). */
    const val MAX_CONCURRENT_APPLICATIONS = 5

    /** Injected [CourierModel] instance for schema-validated DOM planning, field mapping, and grounded synthesis (Phase 1.6). */
    @Volatile
    var courierModel: CourierModel? = null

    /** Injected [CourierWebHost] instance for multi-slot headless WebView orchestration. */
    @Volatile
    var webHost: CourierWebHost? = null

    fun injectDependencies(model: CourierModel, host: CourierWebHost? = null) {
        courierModel = model
        if (host != null) {
            webHost = host
        }
    }

    private val _workerSlots = MutableStateFlow<List<CourierWorkerSlot>>(
        (0 until CourierAgentConfig.maxConcurrentWorkers).map { idx ->
            CourierWorkerSlot(slotIndex = idx, isOccupied = false)
        }
    )
    val workerSlots: StateFlow<List<CourierWorkerSlot>> = _workerSlots.asStateFlow()

    fun observeWorkerSlots(): Flow<List<CourierWorkerSlot>> = workerSlots

    fun updateWorkerSlots(slots: List<CourierWorkerSlot>) {
        _workerSlots.value = slots
    }

    private val _takeoverTaskId = MutableStateFlow<String?>(null)
    val takeoverTaskId: StateFlow<String?> = _takeoverTaskId.asStateFlow()

    fun openTakeover(taskId: String) {
        _takeoverTaskId.value = taskId.takeIf { it.isNotBlank() }
    }

    fun closeTakeover() {
        _takeoverTaskId.value = null
    }

    private val deletedAppIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val initialApplications = listOf(
        QueuedApplication("app_1", "Stripe", "Core Infrastructure & Payments API Engineer", "Greenhouse", "https://boards.greenhouse.io/stripe", "ATS_Resume_stripe.pdf", ApplicationStatus.QUEUED),
        QueuedApplication("app_2", "OpenAI", "AI Research & Agent Infrastructure Engineer", "Ashby", "https://jobs.ashbyhq.com/openai", "ATS_Resume_openai.pdf", ApplicationStatus.IN_TRANSIT),
        QueuedApplication("app_3", "Anthropic", "Prompt Systems & API Backend Engineer", "Greenhouse", "https://boards.greenhouse.io/anthropic", "ATS_Resume_anthropic.pdf", ApplicationStatus.SUBMITTED),
        QueuedApplication("app_4", "Cloudflare", "Distributed Systems & Data Platform Engineer", "Greenhouse", "https://boards.greenhouse.io/cloudflare", "ATS_Resume_cloudflare.pdf", ApplicationStatus.INTERVIEWING),
        QueuedApplication("app_5", "Databricks", "Staff Android & CoreOS Systems Engineer", "Greenhouse", "https://boards.greenhouse.io/databricks", "ATS_Resume_databricks.pdf", ApplicationStatus.QUEUED),
    )

    private val _applications = MutableStateFlow<List<QueuedApplication>>(initialApplications)
    val applications: StateFlow<List<QueuedApplication>> = _applications.asStateFlow()

    private val _roomView = MutableStateFlow(CourierGuildRoomView.AGENT_ROOM)
    val roomView: StateFlow<CourierGuildRoomView> = _roomView.asStateFlow()

    fun setRoomView(view: CourierGuildRoomView) {
        _roomView.value = view
    }

    fun isDeleted(appId: String): Boolean = deletedAppIds.contains(appId)

    fun addApplication(app: QueuedApplication, atIndex: Int = 0) {
        if (deletedAppIds.contains(app.id)) return
        val current = _applications.value.toMutableList()
        if (current.none { it.id == app.id }) {
            if (atIndex in 0..current.size) {
                current.add(atIndex, app)
            } else {
                current.add(app)
            }
            _applications.value = current
        }
    }

    fun updateApplicationStatus(appId: String, newStatus: ApplicationStatus, queuedTime: String? = null) {
        val current = _applications.value.map { app ->
            if (app.id == appId) {
                app.copy(
                    status = newStatus,
                    queuedTime = queuedTime ?: app.queuedTime
                )
            } else {
                app
            }
        }
        _applications.value = current
    }

    fun snoozeApplication(appId: String) {
        val current = _applications.value.toMutableList()
        val idx = current.indexOfFirst { it.id == appId }
        if (idx != -1) {
            val app = current.removeAt(idx)
            val snoozed = app.copy(status = ApplicationStatus.QUEUED, queuedTime = "Snoozed")
            current.add(snoozed)
            _applications.value = current
        }
    }

    fun deleteApplication(appId: String) {
        deletedAppIds.add(appId)
        cancelWorker(appId)
        _applications.value = _applications.value.filter { it.id != appId }
    }

    private val activeWorkerJobs = ConcurrentHashMap<String, Job>()
    private val _activeQuest = MutableStateFlow<CourierCaptchaQuest?>(null)
    val activeQuest: StateFlow<CourierCaptchaQuest?> = _activeQuest.asStateFlow()

    fun reportQuest(quest: CourierCaptchaQuest?) {
        _activeQuest.value = quest
    }

    fun clearQuest() {
        _activeQuest.value = null
    }

    fun canDispatchMore(): Boolean = activeWorkerJobs.size < MAX_CONCURRENT_APPLICATIONS

    fun registerWorker(appId: String, job: Job): Boolean {
        if (activeWorkerJobs.size >= MAX_CONCURRENT_APPLICATIONS) return false
        activeWorkerJobs[appId] = job
        return true
    }

    fun unregisterWorker(appId: String) {
        activeWorkerJobs.remove(appId)
        if (_activeQuest.value?.appId == appId) {
            _activeQuest.value = null
        }
    }

    fun cancelWorker(appId: String): Boolean {
        val job = activeWorkerJobs.remove(appId)
        job?.cancel()
        if (_activeQuest.value?.appId == appId) {
            _activeQuest.value = null
        }
        return job != null
    }

    /**
     * Cancels a WorkManager unique task ("courier-{taskId}"), frees its WebView slot, and marks it CANCELLED
     * (Reconciliation §3: "Cancel Worker" in Agent Room).
     */
    fun cancelWorker(context: Context, taskIdOrAppId: String): Boolean {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork("courier-$taskIdOrAppId")
        }
        return cancelWorker(taskIdOrAppId)
    }

    fun getActiveWorkerCount(): Int = activeWorkerJobs.size

    fun isWorkerActive(appId: String): Boolean = activeWorkerJobs.containsKey(appId)

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}?"
    private const val ALL_CHARS = UPPER + LOWER + DIGITS + SYMBOLS

    private val secureRandom = SecureRandom()

    /**
     * Inspects job board source and target application URL.
     * If `source` or `applicationUrl` contains "LinkedIn" (ignore case), returns `true`
     * to completely exclude the listing from automated application pipelines.
     */
    fun isExcludedFromAutoApply(source: String, applicationUrl: String): Boolean {
        return source.contains("linkedin", ignoreCase = true) ||
            applicationUrl.contains("linkedin", ignoreCase = true)
    }

    /**
     * Convenience overload for [JobListing] inspecting `source`, `applyUrl`, and `atsUrl`.
     */
    fun isExcludedFromAutoApply(job: JobListing): Boolean {
        val targetUrl = job.applyUrl.ifBlank { job.atsUrl }
        return isExcludedFromAutoApply(job.source, targetUrl) ||
            job.atsUrl.contains("linkedin", ignoreCase = true)
    }

    /**
     * Convenience overload for [QueuedApplication] in Courier Guild.
     */
    fun isExcludedFromAutoApply(app: QueuedApplication): Boolean {
        return isExcludedFromAutoApply(app.platform, app.atsUrl)
    }

    /**
     * Opens a Manual-Apply-Only listing directly in an external browser tab.
     */
    fun routeToExternalManualApply(context: Context, rawUrl: String) {
        val safeUrl = rawUrl.trim()
        if (safeUrl.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Generates a cryptographically strong password (18 chars by default) containing
     * guaranteed uppercase, lowercase, digits, and symbols for ATS account creation gates.
     */
    fun generateStrongPassword(length: Int = 18): String {
        val effectiveLength = length.coerceAtLeast(16)
        val chars = MutableList(effectiveLength) {
            ALL_CHARS[secureRandom.nextInt(ALL_CHARS.length)]
        }
        // Guarantee at least one character from each character class
        chars[0] = UPPER[secureRandom.nextInt(UPPER.length)]
        chars[1] = LOWER[secureRandom.nextInt(LOWER.length)]
        chars[2] = DIGITS[secureRandom.nextInt(DIGITS.length)]
        chars[3] = SYMBOLS[secureRandom.nextInt(SYMBOLS.length)]
        chars.shuffle(secureRandom)
        return chars.joinToString("")
    }

    // ---------------------------------------------------------------------------
    // Field-level AES-GCM encryption layer on top of SQLCipher database storage
    // ---------------------------------------------------------------------------

    private val vaultKeyBytes = "GrandExchangeCourierVaultKey2026".toByteArray(Charsets.UTF_8).copyOf(32)

    fun encryptPassword(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return runCatching {
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(vaultKeyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            "ENC1:" + Base64.getEncoder().encodeToString(combined)
        }.getOrDefault(plaintext)
    }

    fun decryptPassword(encrypted: String): String {
        if (!encrypted.startsWith("ENC1:")) return encrypted
        return runCatching {
            val raw = Base64.getDecoder().decode(encrypted.removePrefix("ENC1:"))
            val iv = raw.copyOfRange(0, 12)
            val cipherBytes = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(vaultKeyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault(encrypted)
    }
}
