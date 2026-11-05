package com.osrs.careerguild.presentation.dashboard.courierguild

import android.content.Context
import com.osrs.careerguild.core.github.GitHubRepo
import com.osrs.careerguild.core.pdf.ResumeForgePdfGenerator
import com.osrs.careerguild.core.profile.UserProfile
import com.osrs.careerguild.core.profile.UserProfileManager
import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import com.osrs.careerguild.data.local.entity.FieldMappingCacheEntity
import com.osrs.careerguild.data.local.entity.toDomain
import com.osrs.careerguild.di.AppContainer
import com.osrs.careerguild.domain.resume.ResumeVerifier
import com.osrs.careerguild.domain.resume.TailoredResume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import kotlin.random.Random

/**
 * Deterministic-first, model-second execution engine for the Courier background worker
 * (Phases 2, 3, and 4 + Reconciliation §1–§8).
 */
object CourierPipelineExecutor {

    /**
     *Deterministic decline synonym table for EEO portal matching (Reconciliation §4).
     */
    val DECLINE_SYNONYMS = listOf(
        "decline to self-identify",
        "decline to self identify",
        "i decline to self-identify",
        "i decline to self-identify for protected veteran status",
        "prefer not to say",
        "i prefer not to say",
        "i do not wish to answer",
        "do not wish to answer",
        "i choose not to disclose",
        "choose not to disclose",
    )

    data class ForgePreparationOutcome(
        val pdfFile: File?,
        val tailoredResume: TailoredResume?,
        val violations: List<String> = emptyList(),
        val errorMessage: String? = null,
    )

    data class EarlyBlockOutcome(
        val reason: CourierBlockedReason,
        val blockedFields: List<String>,
        val detail: String,
    )

    data class NavigationOutcome(
        val formReached: Boolean,
        val currentUrl: String,
        val blockedReason: CourierBlockedReason? = null,
        val detail: String = "",
    )

    data class FillFormOutcome(
        val currentUrl: String,
        val blockedReason: CourierBlockedReason? = null,
        val blockedFields: List<String> = emptyList(),
        val detail: String = "",
        val reviewRequired: Boolean = false,
        val allRequiredMatched: Boolean = false,
        val isSupportedAtsAdapter: Boolean = false,
        val submitElementId: String? = null,
    )

    /**
     * Phase 2.1 & Reconciliation §7:
     * Uses the existing verified Forge resume for this exact job if one exists; otherwise calls
     * `ResumeTailor` + `ResumeVerifier` (up to 2 verification attempts) and generates the 1-page PDF
     * via `ResumeForgePdfGenerator`. Blocks with `RESUME_VERIFICATION_FAILED` if verification fails twice.
     */
    suspend fun ensureVerifiedForgeResume(
        context: Context,
        task: CourierTaskEntity,
        container: AppContainer,
    ): ForgePreparationOutcome = withContext(Dispatchers.IO) {
        val baseResume = container.baseResumeRepository.getBaseResume()
            ?: return@withContext ForgePreparationOutcome(
                pdfFile = null,
                tailoredResume = null,
                violations = listOf("BaseResume not uploaded"),
                errorMessage = "BaseResume is missing; upload your resume in Scribe Tower first.",
            )

        val repos = loadCachedGitHubRepos(container)
        val profile = UserProfileManager.getInstance(context).profile.value
        val jobListing = container.jobRepository.getJobById(task.jobId)
        val jobDescription = jobListing?.description.orEmpty().ifBlank { "${task.title} at ${task.company}" }

        // 1. Check if a verified TailoredResume + PDF already exist for this job
        val existingTailored = container.tailoredResumeStore.getTailoredResume(task.jobId)
        if (existingTailored != null) {
            val check = ResumeVerifier.verify(existingTailored, baseResume.activeOnly(), repos)
            if (check.isValid) {
                val existingPdf = task.resumePdfPath?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
                    ?: ResumeForgePdfGenerator.forgeAtsResume(
                        context = context,
                        tailoredResume = existingTailored,
                        targetTitle = task.title,
                        targetCompany = task.company,
                    ).getOrNull()?.file
                if (existingPdf != null && existingPdf.exists()) {
                    return@withContext ForgePreparationOutcome(
                        pdfFile = existingPdf,
                        tailoredResume = existingTailored,
                    )
                }
            }
        }

        // 2. Otherwise tailor and verify up to 2 attempts (Phase 2.1: if verification fails twice, block)
        var lastViolations = emptyList<String>()
        for (attempt in 1..2) {
            val candidate = if (attempt == 1) {
                container.resumeTailor.tailor(jobDescription, baseResume, repos)
            } else {
                container.resumeTailor.tailorDeterministically(jobDescription, baseResume.activeOnly(), repos)
            }
            val verification = ResumeVerifier.verify(candidate, baseResume.activeOnly(), repos)
            if (verification.isValid) {
                container.tailoredResumeStore.saveTailoredResume(task.jobId, candidate)
                val pdfRes = ResumeForgePdfGenerator.forgeAtsResume(
                    context = context,
                    tailoredResume = candidate,
                    targetTitle = task.title.ifBlank { profile.name },
                    targetCompany = task.company.ifBlank { "Company" },
                )
                val pdfFile = pdfRes.getOrNull()?.file
                if (pdfFile != null && pdfFile.exists()) {
                    return@withContext ForgePreparationOutcome(
                        pdfFile = pdfFile,
                        tailoredResume = candidate,
                    )
                } else {
                    return@withContext ForgePreparationOutcome(
                        pdfFile = null,
                        tailoredResume = candidate,
                        violations = listOf("PDF generation failed"),
                        errorMessage = pdfRes.exceptionOrNull()?.message ?: "PDF generation failed",
                    )
                }
            }
            lastViolations = verification.violations
        }

        ForgePreparationOutcome(
            pdfFile = null,
            tailoredResume = null,
            violations = lastViolations,
            errorMessage = "ResumeVerifier rejected tailored resume twice: ${lastViolations.joinToString("; ")}",
        )
    }

    /**
     * Phase 2.3 & Reconciliation §3, §6:
     * Detects early blocks:
     * - `CAPTCHA_OR_CHALLENGE` (via [CourierGuardrails])
     * - `VIDEO_OR_ASSESSMENT` (hard stop via [CourierGuardrails])
     * - `UNSUPPORTED_ATS` (Workday, LinkedIn)
     * - `LOGIN_REQUIRED`: if [CourierAccountDao] has credentials for the ATS host and it is a plain
     *   sign-in form (not account creation / MFA / OTP), fills credentials directly in code;
     *   otherwise blocks with `LOGIN_REQUIRED`. Never creates accounts automatically.
     */
    suspend fun checkEarlyBlocksAndLogin(
        task: CourierTaskEntity,
        webHost: CourierWebHost,
        container: AppContainer,
    ): EarlyBlockOutcome? {
        val currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { task.currentUrl.ifBlank { task.jobUrl } }
        val atsDetected = CourierAgentConfig.detectAtsPlatform(currentUrl, task.ats).lowercase()
        if (atsDetected.contains("workday") || atsDetected.contains("linkedin")) {
            return EarlyBlockOutcome(
                reason = CourierBlockedReason.UNSUPPORTED_ATS,
                blockedFields = listOf("Unsupported ATS ($atsDetected)"),
                detail = "Unsupported ATS platform requires manual takeover",
            )
        }

        when (val probe = webHost.runGuardrailsProbe(task.taskId)) {
            is CourierGuardrails.ProbeResult.Captcha -> {
                return EarlyBlockOutcome(
                    reason = CourierBlockedReason.CAPTCHA_OR_CHALLENGE,
                    blockedFields = listOf("CAPTCHA: ${probe.kind}"),
                    detail = "Challenge detected (${probe.kind}); handing off to user",
                )
            }
            is CourierGuardrails.ProbeResult.Handoff -> {
                return EarlyBlockOutcome(
                    reason = CourierBlockedReason.VIDEO_OR_ASSESSMENT,
                    blockedFields = listOf(probe.kind),
                    detail = "Mandatory video/assessment detected (${probe.kind}); hard stop",
                )
            }
            is CourierGuardrails.ProbeResult.Clear -> Unit
        }

        val snapshot = webHost.extractDomSnapshot(task.taskId)
        val passwordControls = snapshot.filter {
            it.type.equals("password", ignoreCase = true) ||
                it.name.contains("password", ignoreCase = true)
        }
        val mfaOrSignupControls = snapshot.filter {
            val combined = "${it.label} ${it.name} ${it.id} ${it.placeholder}".lowercase()
            Regex("verification code|security code|one-?time|otp|two-?factor|2fa|mfa|confirm password|create (an )?account|sign up").containsMatchIn(combined)
        }

        if (mfaOrSignupControls.isNotEmpty() && passwordControls.isNotEmpty()) {
            // Signup / MFA / emailed code -> block immediately (Reconciliation §6: Never create accounts automatically)
            return EarlyBlockOutcome(
                reason = CourierBlockedReason.LOGIN_REQUIRED,
                blockedFields = listOf("Account creation / MFA / verification code required"),
                detail = "Portal requires account creation or MFA verification",
            )
        }

        if (passwordControls.isNotEmpty()) {
            val savedAccount = container.courierAccountDao.findByPortalOrCompany(currentUrl, task.company)
                ?: container.courierAccountDao.findByPortalOrCompany(task.jobUrl, task.company)

            if (savedAccount == null) {
                return EarlyBlockOutcome(
                    reason = CourierBlockedReason.LOGIN_REQUIRED,
                    blockedFields = listOf("Login credentials required for ${task.company}"),
                    detail = "No saved credentials in CourierAccountDao for ${task.company}",
                )
            }

            // Fill saved vault credentials directly in code (never through the model!)
            val emailControl = snapshot.firstOrNull {
                it.type.equals("email", ignoreCase = true) ||
                    it.name.contains("email", ignoreCase = true) ||
                    it.name.contains("user", ignoreCase = true) ||
                    it.label.contains("email", ignoreCase = true)
            }
            val pwdControl = passwordControls.first()
            val plainPassword = CourierAutoApplyDispatcher.decryptPassword(savedAccount.passwordEncrypted)

            if (emailControl != null) {
                webHost.fillElementValueSafely(task.taskId, emailControl.elementId, savedAccount.emailUsed)
                humanPaceDelay()
            }
            webHost.fillElementValueSafely(task.taskId, pwdControl.elementId, plainPassword)
            humanPaceDelay()

            val signInBtn = snapshot.firstOrNull {
                it.tag in setOf("button", "input", "a") &&
                    Regex("sign in|log in|login|submit|continue", RegexOption.IGNORE_CASE).containsMatchIn(it.label)
            }
            if (signInBtn != null) {
                webHost.clickElementSafely(task.taskId, signInBtn.elementId)
                delay(1500L)
            }

            // Re-snapshot to verify login succeeded
            val afterLogin = webHost.extractDomSnapshot(task.taskId)
            val stillHasPassword = afterLogin.any { it.type.equals("password", ignoreCase = true) }
            if (stillHasPassword) {
                return EarlyBlockOutcome(
                    reason = CourierBlockedReason.LOGIN_REQUIRED,
                    blockedFields = listOf("Vault login did not clear login wall"),
                    detail = "Login wall remained active after vault credential fill",
                )
            }
        }

        return null
    }

    /**
     * Phase 2.2:
     * Dismisses cookie banners and navigates to the application form in at most 2 attempts.
     * Blocks with `NO_APPLY_PATH` if the form cannot be reached after 2 attempts.
     */
    suspend fun navigateToApplicationForm(
        task: CourierTaskEntity,
        webHost: CourierWebHost,
        courierModel: CourierModel,
    ): NavigationOutcome {
        var currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { task.currentUrl.ifBlank { task.jobUrl } }

        for (attempt in 1..2) {
            val snapshot = webHost.extractDomSnapshot(task.taskId)

            // 1. Handle cookie banner separately first ("reject non-essential" / "necessary only", else close)
            CourierApplyTriggerFinder.findCookieBannerDismiss(snapshot)?.let { cookieBtn ->
                webHost.clickElementSafely(task.taskId, cookieBtn.elementId)
                delay(450L)
            }

            val refreshed = webHost.extractDomSnapshot(task.taskId)
            val selection = CourierApplyTriggerFinder.selectApplyTrigger(refreshed, currentUrl)
            if (selection.formAlreadyVisible) {
                return NavigationOutcome(
                    formReached = true,
                    currentUrl = currentUrl,
                    detail = "Application form visible on attempt $attempt",
                )
            }

            var chosenCandidate: DomControlSnapshot? = selection.selectedCandidate

            // Call model ONLY if top two scores are within threshold or none pass, sending only pruned candidates
            if (chosenCandidate == null && selection.needsModelTieBreak && selection.prunedCandidates.isNotEmpty()) {
                val planned = courierModel.planAction(
                    candidates = selection.prunedCandidates,
                    stepContext = "Select the job application Apply button (attempt $attempt)",
                    escalate = attempt > 1,
                ).getOrNull()
                if (planned?.actionType == AllowedActionType.CLICK_ELEMENT && planned.elementId != null) {
                    chosenCandidate = selection.prunedCandidates.firstOrNull { it.elementId == planned.elementId }
                }
            }

            if (chosenCandidate != null) {
                webHost.clickElementSafely(task.taskId, chosenCandidate.elementId)
                delay(1200L)
                currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { currentUrl }
                val postClickSnapshot = webHost.extractDomSnapshot(task.taskId)
                if (CourierApplyTriggerFinder.isApplicationFormVisible(postClickSnapshot, currentUrl)) {
                    return NavigationOutcome(
                        formReached = true,
                        currentUrl = currentUrl,
                        detail = "Reached form via Apply trigger '${chosenCandidate.label}'",
                    )
                }
            } else {
                // Check direct ATS adapter apply URL (Lever {job}/apply, Ashby .../application)
                val directApplyUrl = CourierApplyTriggerFinder.deriveDirectAtsApplyUrl(currentUrl)
                if (directApplyUrl != null && directApplyUrl != currentUrl) {
                    currentUrl = webHost.loadUrlAndWait(task.taskId, directApplyUrl) ?: directApplyUrl
                    val postLoadSnapshot = webHost.extractDomSnapshot(task.taskId)
                    if (CourierApplyTriggerFinder.isApplicationFormVisible(postLoadSnapshot, currentUrl)) {
                        return NavigationOutcome(
                            formReached = true,
                            currentUrl = currentUrl,
                            detail = "Reached form via direct ATS apply URL",
                        )
                    }
                }
            }
        }

        return NavigationOutcome(
            formReached = false,
            currentUrl = currentUrl,
            blockedReason = CourierBlockedReason.NO_APPLY_PATH,
            detail = "Failed to locate application form after 2 navigation attempts",
        )
    }

    /**
     * Phases 2.4–3.6 & Reconciliation §4, §5, §7, §8:
     * 1. Uploads the Forged resume FIRST (`attachResumeFirst`) and waits for autofill to settle.
     * 2. Re-snapshots the DOM.
     * 3. Overwrites every standard contact/profile field with verified values from [UserProfileManager].
     * 4. Populates EEO fields (`gender`, `race`, `veteranStatus`) strictly per `instruction.md` §8 and
     *    the deterministic decline synonym table, blocking if no option matches.
     * 5. Populates protected/legal fields strictly from `ApplicantAnswers`, blocking if missing.
     * 6. Uses `field_mapping_cache` and `CourierModel.mapFields` for unfamiliar labels, storing verified
     *    payload keys in `field_mapping_cache`.
     * 7. Synthesizes open-ended `EXPERIENCE` and `MOTIVATION` questions from a numbered Fact Sheet
     *    (`F1..Fn`), verifies with [CourierAnswerVerifier] (with 1 retry), and sets `reviewRequired = true`
     *    for `MOTIVATION` questions.
     */
    suspend fun uploadAndFillForm(
        context: Context,
        task: CourierTaskEntity,
        forgePdfFile: File,
        tailoredResume: TailoredResume?,
        filledFields: MutableMap<String, String>,
        webHost: CourierWebHost,
        container: AppContainer,
    ): FillFormOutcome {
        val profile = UserProfileManager.getInstance(context).profile.value
        val baseResume = container.baseResumeRepository.getBaseResume()
        val storedAnswers = container.applicantAnswerDao.getAll().associate {
            it.questionPattern.lowercase().trim() to it.answer.trim()
        }
        val repos = loadCachedGitHubRepos(container)
        val jobListing = container.jobRepository.getJobById(task.jobId)
        val jobDescription = jobListing?.description.orEmpty().ifBlank { "${task.title} at ${task.company}" }

        // 1. Upload resume FIRST (Phase 2.4 & Reconciliation §7)
        val uploaded = webHost.attachResumeFirst(task.taskId, forgePdfFile)
        if (uploaded) {
            filledFields["resume_upload"] = forgePdfFile.name
            humanPaceDelay()
        }

        // 2. Re-snapshot after upload/autofill
        val snapshot = webHost.extractDomSnapshot(task.taskId)
        val currentUrl = webHost.getCurrentUrl(task.taskId).ifBlank { task.currentUrl.ifBlank { task.jobUrl } }
        val host = extractHost(currentUrl).ifBlank { task.ats.lowercase() }

        val blockedFields = mutableListOf<String>()
        var reviewRequired = false
        var submitElementId: String? = null

        val payloadMap = buildPayloadValueMap(profile, baseResume, storedAnswers)
        val unmappedControls = mutableListOf<DomControlSnapshot>()

        for (ctrl in snapshot) {
            val tag = ctrl.tag.lowercase()
            val type = ctrl.type.lowercase()

            // Identify submit button candidate
            if ((tag == "button" || (tag == "input" && type == "submit")) &&
                Regex("submit( application)?|send application|finish|apply", RegexOption.IGNORE_CASE).containsMatchIn(ctrl.label)
            ) {
                submitElementId = ctrl.elementId
                continue
            }

            if (tag !in setOf("input", "select", "textarea") && !ctrl.containerHint.contains("combobox", ignoreCase = true)) {
                continue
            }
            if (type in setOf("file", "button", "submit", "reset", "hidden")) {
                continue
            }

            val questionText = "${ctrl.label} ${ctrl.placeholder} ${ctrl.name} ${ctrl.id}".trim()
            val normalizedLabel = GeminiCourierModel.normalizeFieldLabel(ctrl.label.ifBlank { ctrl.placeholder.ifBlank { ctrl.name } })

            // A. Check EEO gender / race / veteran first (Reconciliation §4 & instruction.md §8)
            val eeoKind = classifyEeoKind(questionText)
            if (eeoKind != null) {
                val targetValue = when (eeoKind) {
                    AllowedPayloadKey.EEO_GENDER -> CourierResponseSynthesizer.normalizeGender(profile.gender)
                    AllowedPayloadKey.EEO_RACE -> CourierResponseSynthesizer.normalizeRace(profile.race)
                    AllowedPayloadKey.EEO_VETERAN -> CourierResponseSynthesizer.normalizeVeteranStatus(profile.veteranStatus)
                    else -> ""
                }
                val matched = fillEeoControlDeterministic(task.taskId, ctrl, targetValue, webHost)
                if (matched != null) {
                    filledFields[eeoKind.key] = matched
                    humanPaceDelay()
                } else {
                    blockedFields.add(ctrl.label.ifBlank { eeoKind.key })
                }
                continue
            }

            // B. Check deterministic standard payload key or field_mapping_cache
            val deterministicKey = matchDeterministicPayloadKey(ctrl)
                ?: container.fieldMappingCacheDao.findMapping(host, normalizedLabel)?.let {
                    AllowedPayloadKey.fromKey(it.payloadKey)
                }

            if (deterministicKey != null) {
                // Phase 2.5: Never trust ATS autofill — read back and overwrite if different
                val desiredValue = payloadMap[deterministicKey]?.trim().orEmpty()
                if (desiredValue.isBlank()) {
                    // Protected / legal questions with no answer in ApplicantAnswers MUST block
                    if (isProtectedKey(deterministicKey) || ctrl.required) {
                        blockedFields.add(ctrl.label.ifBlank { deterministicKey.key })
                    }
                } else {
                    val currentDomVal = webHost.readElementValueSafely(task.taskId, ctrl.elementId)
                    if (!valuesMatch(currentDomVal, desiredValue)) {
                        val ok = webHost.fillElementValueSafely(task.taskId, ctrl.elementId, desiredValue)
                        if (ok) {
                            filledFields[deterministicKey.key] = desiredValue
                            humanPaceDelay()
                        } else if (ctrl.required) {
                            blockedFields.add(ctrl.label.ifBlank { deterministicKey.key })
                        }
                    } else {
                        filledFields[deterministicKey.key] = desiredValue
                    }
                }
                continue
            }

            // C. Classify question via CourierResponseSynthesizer (Phase 3.2)
            when (CourierResponseSynthesizer.classifyCategory(questionText)) {
                CourierResponseSynthesizer.QuestionCategory.PROTECTED -> {
                    val customMatch = findApplicantAnswerMatch(questionText, storedAnswers)
                    if (customMatch.isNullOrBlank()) {
                        blockedFields.add(ctrl.label.ifBlank { questionText.take(80) })
                    } else {
                        webHost.fillElementValueSafely(task.taskId, ctrl.elementId, customMatch)
                        filledFields[normalizedLabel.ifBlank { ctrl.elementId }] = customMatch
                        humanPaceDelay()
                    }
                }
                CourierResponseSynthesizer.QuestionCategory.EEO_PROFILE -> {
                    // Already handled above, fallback block if reached
                    blockedFields.add(ctrl.label.ifBlank { "EEO field" })
                }
                CourierResponseSynthesizer.QuestionCategory.EXPERIENCE,
                CourierResponseSynthesizer.QuestionCategory.MOTIVATION -> {
                    val isMotivation = CourierResponseSynthesizer.classifyCategory(questionText) ==
                        CourierResponseSynthesizer.QuestionCategory.MOTIVATION
                    if (isMotivation) {
                        // Phase 3.2: MOTIVATION forces review_required = true even under AUTO_SUBMIT
                        reviewRequired = true
                    }
                    val synthOutcome = synthesizeAndVerifyGroundedAnswer(
                        question = ctrl.label.ifBlank { questionText },
                        maxLength = ctrl.maxLength,
                        task = task,
                        baseResume = baseResume,
                        tailoredResume = tailoredResume,
                        repos = repos,
                        jobDescription = jobDescription,
                        profile = profile,
                        courierModel = container.courierModel,
                    )
                    if (synthOutcome == null) {
                        return FillFormOutcome(
                            currentUrl = currentUrl,
                            blockedReason = CourierBlockedReason.MISSING_GROUNDING,
                            blockedFields = listOf(ctrl.label.ifBlank { questionText.take(120) }),
                            detail = "Missing grounded facts to answer: '${ctrl.label.ifBlank { questionText.take(120) }}'",
                            reviewRequired = reviewRequired,
                        )
                    }
                    webHost.fillElementValueSafely(task.taskId, ctrl.elementId, synthOutcome)
                    filledFields[normalizedLabel.ifBlank { ctrl.elementId }] = synthOutcome
                    humanPaceDelay()
                }
                CourierResponseSynthesizer.QuestionCategory.STANDARD_PERSONAL -> {
                    val customMatch = findApplicantAnswerMatch(questionText, storedAnswers)
                    if (!customMatch.isNullOrBlank()) {
                        webHost.fillElementValueSafely(task.taskId, ctrl.elementId, customMatch)
                        filledFields[normalizedLabel.ifBlank { ctrl.elementId }] = customMatch
                        humanPaceDelay()
                    } else {
                        unmappedControls.add(ctrl)
                    }
                }
            }
        }

        // D. Model field mapping for remaining unmapped controls + caching in field_mapping_cache (Phase 3.6)
        if (unmappedControls.isNotEmpty()) {
            val mappings = container.courierModel.mapFields(unmappedControls).getOrDefault(emptyList())
            val mappedIds = mutableSetOf<String>()
            for (mapping in mappings) {
                val ctrl = unmappedControls.firstOrNull { it.elementId == mapping.elementId } ?: continue
                mappedIds.add(ctrl.elementId)
                val value = payloadMap[mapping.payloadKey]?.trim().orEmpty()
                if (value.isBlank()) {
                    if (ctrl.required || isProtectedKey(mapping.payloadKey)) {
                        blockedFields.add(ctrl.label.ifBlank { mapping.payloadKey.key })
                    }
                    continue
                }
                val filledOk = webHost.fillElementValueSafely(task.taskId, ctrl.elementId, value)
                val readBack = webHost.readElementValueSafely(task.taskId, ctrl.elementId)
                if (filledOk && valuesMatch(readBack, value)) {
                    filledFields[mapping.payloadKey.key] = value
                    if (mapping.normalizedLabel.isNotBlank()) {
                        container.fieldMappingCacheDao.upsertMapping(
                            FieldMappingCacheEntity(
                                host = host,
                                normalizedLabel = mapping.normalizedLabel,
                                payloadKey = mapping.payloadKey.key,
                            )
                        )
                    }
                    humanPaceDelay()
                } else if (ctrl.required) {
                    blockedFields.add(ctrl.label.ifBlank { mapping.payloadKey.key })
                }
            }

            // Any required control still unmapped and unfilled blocks
            for (unmapped in unmappedControls.filter { it.elementId !in mappedIds }) {
                if (unmapped.required && !unmapped.currentValuePresent) {
                    blockedFields.add(unmapped.label.ifBlank { unmapped.name.ifBlank { unmapped.elementId } })
                }
            }
        }

        if (blockedFields.isNotEmpty()) {
            return FillFormOutcome(
                currentUrl = currentUrl,
                blockedReason = CourierBlockedReason.MISSING_REQUIRED_FIELDS,
                blockedFields = blockedFields.distinct(),
                detail = "Missing required or protected fields: ${blockedFields.distinct().joinToString(", ")}",
                reviewRequired = reviewRequired,
            )
        }

        // E. Verify all required fields read back non-blank for AUTO_SUBMIT gate (Phase 4.1)
        val postFillSnapshot = webHost.extractDomSnapshot(task.taskId)
        val allRequiredFilled = postFillSnapshot
            .filter { it.required && it.tag.lowercase() in setOf("input", "select", "textarea") && it.type.lowercase() != "file" }
            .all { it.currentValuePresent || webHost.readElementValueSafely(task.taskId, it.elementId).isNotBlank() }

        val atsName = CourierAgentConfig.detectAtsPlatform(currentUrl, task.ats).lowercase()
        val isSupportedAdapter = atsName in setOf("greenhouse", "lever", "ashby") ||
            currentUrl.contains("appassets.androidplatform.net")

        return FillFormOutcome(
            currentUrl = currentUrl,
            blockedReason = null,
            blockedFields = emptyList(),
            detail = "Form filled and verified",
            reviewRequired = reviewRequired,
            allRequiredMatched = allRequiredFilled,
            isSupportedAtsAdapter = isSupportedAdapter,
            submitElementId = submitElementId,
        )
    }

    /**
     * Phase 4.2: Clicks submit exactly once and checks for ATS-specific confirmation URL or text.
     */
    suspend fun clickSubmitAndVerifyConfirmation(
        task: CourierTaskEntity,
        submitElementId: String,
        webHost: CourierWebHost,
    ): Boolean {
        val clicked = webHost.clickElementSafely(task.taskId, submitElementId)
        if (!clicked) return false
        delay(2000L)
        val currentUrl = webHost.getCurrentUrl(task.taskId)
        if (isConfirmationUrl(currentUrl)) return true
        val bodyText = webHost.evaluateJs(
            task.taskId,
            "(function(){ return (document.body && document.body.innerText) ? document.body.innerText.substring(0, 4000) : ''; })()"
        ).orEmpty()
        return isConfirmationText(bodyText)
    }

    /**
     * Builds the numbered Fact Sheet (`F1..Fn`) from BaseResume, TailoredResume, GitHub cache, and JD (Phase 3.3).
     */
    fun buildFactSheet(
        task: CourierTaskEntity,
        baseResume: BaseResume?,
        tailoredResume: TailoredResume?,
        repos: List<GitHubRepo>,
        jobDescription: String,
        profileGithub: String = "",
    ): List<FactItem> {
        val facts = mutableListOf<FactItem>()
        var idx = 1
        fun addFact(source: String, text: String?) {
            val clean = text?.trim().orEmpty()
            if (clean.isNotBlank()) {
                facts.add(FactItem(id = "F${idx++}", source = source, text = clean))
            }
        }

        addFact("Target Role", "Applying for ${task.title} at ${task.company}.")
        if (jobDescription.isNotBlank()) {
            addFact("Job Description", jobDescription.take(600))
        }
        if (profileGithub.isNotBlank()) {
            addFact("Profile:GitHub", "GitHub portfolio: $profileGithub.")
        }

        val activeBase = baseResume?.activeOnly()
        if (activeBase != null) {
            if (!activeBase.contact.github.isNullOrBlank()) {
                addFact("BaseResume:GitHub", "GitHub portfolio: ${activeBase.contact.github}.")
            }
            if (activeBase.skills.isNotEmpty()) {
                addFact("BaseResume:Skills", "Core technical skills: ${activeBase.skills.joinToString(", ")}.")
            }
            activeBase.experience.forEach { exp ->
                addFact(
                    "BaseResume:Experience(${exp.employer})",
                    "Worked as ${exp.title} at ${exp.employer} (${exp.startDate.orEmpty()} - ${exp.endDate.orEmpty()}): ${exp.bullets.joinToString(" ")}"
                )
            }
            activeBase.projects.forEach { proj ->
                addFact(
                    "BaseResume:Project(${proj.name})",
                    "Project ${proj.name} (${proj.technologies.joinToString(", ")}): ${proj.bullets.joinToString(" ")}"
                )
            }
            activeBase.education.forEach { edu ->
                addFact(
                    "BaseResume:Education(${edu.school})",
                    "Degree: ${edu.degree} from ${edu.school}."
                )
            }
        }

        if (tailoredResume != null && tailoredResume.summary.isNotBlank()) {
            addFact("TailoredResume:Summary", tailoredResume.summary)
        }

        repos.take(8).forEach { repo ->
            val desc = repo.description?.takeIf { it.isNotBlank() } ?: "Public software repository"
            val langs = if (repo.languages.isNotEmpty()) " Languages: ${repo.languages.joinToString(", ")}." else ""
            val readme = repo.readmeExcerpt?.take(300)?.let { " Excerpt: $it" }.orEmpty()
            addFact("GitHubRepo:${repo.name}", "Repository ${repo.name}: $desc.$langs$readme")
        }

        return facts
    }

    private suspend fun synthesizeAndVerifyGroundedAnswer(
        question: String,
        maxLength: Int,
        task: CourierTaskEntity,
        baseResume: BaseResume?,
        tailoredResume: TailoredResume?,
        repos: List<GitHubRepo>,
        jobDescription: String,
        profile: UserProfile,
        courierModel: CourierModel,
    ): String? {
        val effectiveGithub = profile.github.ifBlank { baseResume?.contact?.github.orEmpty() }
        val factSheet = buildFactSheet(task, baseResume, tailoredResume, repos, jobDescription, effectiveGithub)
        if (factSheet.isEmpty()) return null

        // Attempt 1 via CourierModel
        val firstAttempt = courierModel.synthesizeAnswer(
            question = question,
            maxLength = maxLength,
            factSheet = factSheet,
            violationFeedback = emptyList(),
            escalate = false,
        ).getOrNull()

        if (firstAttempt != null && !firstAttempt.unsupported) {
            val verify1 = CourierAnswerVerifier.verify(firstAttempt, factSheet, maxLength)
            if (verify1.isValid) {
                return firstAttempt.answer
            }
            // Retry once with the violation list (Phase 3.4)
            val secondAttempt = courierModel.synthesizeAnswer(
                question = question,
                maxLength = maxLength,
                factSheet = factSheet,
                violationFeedback = verify1.violations,
                escalate = true,
            ).getOrNull()
            if (secondAttempt != null && !secondAttempt.unsupported) {
                val verify2 = CourierAnswerVerifier.verify(secondAttempt, factSheet, maxLength)
                if (verify2.isValid) {
                    return secondAttempt.answer
                }
            }
        }

        // Deterministic fallback via existing CourierResponseSynthesizer verified by CourierAnswerVerifier
        val grounding = CourierResponseSynthesizer.groundingFrom(
            base = baseResume,
            company = task.company,
            title = task.title,
            github = effectiveGithub,
            linkedin = baseResume?.contact?.linkedin.orEmpty(),
            phone = profile.phone,
            gender = profile.gender,
            race = profile.race,
            veteranStatus = profile.veteranStatus,
        )
        val deterministicAnswer = CourierResponseSynthesizer.answer(question, grounding, maxLength) ?: return null
        val syntheticResult = GroundedSynthesisResult(
            answer = deterministicAnswer,
            usedFactIds = factSheet.map { it.id },
            unsupported = false,
        )
        val check = CourierAnswerVerifier.verify(syntheticResult, factSheet, maxLength)
        if (!check.isValid) {
            android.util.Log.w("CourierWorker", "Verifier rejected deterministic synthesis: ${check.violations}")
        }
        return if (check.isValid) deterministicAnswer else null
    }

    /**
     * Matches portal EEO options by normalized exact text first, then by [DECLINE_SYNONYMS] when
     * the profile value is a decline option (Reconciliation §4). Never uses the model.
     */
    fun matchPortalEeoOption(
        portalOptions: List<String>,
        profileTarget: String,
    ): String? {
        val normTarget = normalizeOptionText(profileTarget)
        if (normTarget.isBlank()) return null

        // 1. Normalized exact text match
        portalOptions.firstOrNull { normalizeOptionText(it) == normTarget }?.let { return it }

        // 2. Deterministic decline synonym table match if target is a decline option
        val isDeclineTarget = DECLINE_SYNONYMS.any { normalizeOptionText(it) == normTarget } ||
            normTarget.contains("decline to self")
        if (isDeclineTarget) {
            val declineNormSet = DECLINE_SYNONYMS.map { normalizeOptionText(it) }.toSet()
            portalOptions.firstOrNull { opt ->
                val normOpt = normalizeOptionText(opt)
                normOpt in declineNormSet || DECLINE_SYNONYMS.any { syn -> normOpt.contains(normalizeOptionText(syn)) }
            }?.let { return it }
        }

        return null
    }

    private suspend fun fillEeoControlDeterministic(
        taskId: String,
        ctrl: DomControlSnapshot,
        targetValue: String,
        webHost: CourierWebHost,
    ): String? {
        if (ctrl.tag.equals("select", ignoreCase = true) && ctrl.options.isNotEmpty()) {
            val matchedOption = matchPortalEeoOption(ctrl.options, targetValue) ?: return null
            val ok = webHost.fillElementValueSafely(taskId, ctrl.elementId, matchedOption)
            return if (ok) matchedOption else null
        }
        val ok = webHost.fillElementValueSafely(taskId, ctrl.elementId, targetValue)
        return if (ok) targetValue else null
    }

    internal fun classifyEeoKind(questionText: String): AllowedPayloadKey? {
        val q = questionText.lowercase()
        return when {
            Regex("\\bgender\\b|\\bsex\\b").containsMatchIn(q) && !Regex("orientation").containsMatchIn(q) ->
                AllowedPayloadKey.EEO_GENDER
            Regex("\\brace\\b|ethnic").containsMatchIn(q) ->
                AllowedPayloadKey.EEO_RACE
            Regex("veteran|military status").containsMatchIn(q) ->
                AllowedPayloadKey.EEO_VETERAN
            else -> null
        }
    }

    internal fun matchDeterministicPayloadKey(ctrl: DomControlSnapshot): AllowedPayloadKey? {
        val combined = "${ctrl.name} ${ctrl.id} ${ctrl.label} ${ctrl.placeholder}".lowercase()
        return when {
            Regex("first[_ -]?name|given[_ -]?name").containsMatchIn(combined) -> AllowedPayloadKey.FIRST_NAME
            Regex("last[_ -]?name|family[_ -]?name|surname").containsMatchIn(combined) -> AllowedPayloadKey.LAST_NAME
            Regex("^name$|full[_ -]?name|candidate[_ -]?name|your[_ -]?name").containsMatchIn(combined) -> AllowedPayloadKey.FULL_NAME
            ctrl.type.equals("email", ignoreCase = true) || Regex("\\be-?mail\\b").containsMatchIn(combined) -> AllowedPayloadKey.EMAIL
            ctrl.type.equals("tel", ignoreCase = true) || Regex("\\b(phone|mobile|telephone|cell)\\b").containsMatchIn(combined) -> AllowedPayloadKey.PHONE
            Regex("github").containsMatchIn(combined) -> AllowedPayloadKey.GITHUB_URL
            Regex("linkedin").containsMatchIn(combined) -> AllowedPayloadKey.LINKEDIN_URL
            Regex("website|portfolio|personal[_ -]?site").containsMatchIn(combined) -> AllowedPayloadKey.WEBSITE_URL
            Regex("authori[sz]ed to work|work[_ -]?authori[sz]ation|legally authori[sz]ed").containsMatchIn(combined) -> AllowedPayloadKey.WORK_AUTHORIZATION
            Regex("sponsorship|require[_ -]?visa").containsMatchIn(combined) -> AllowedPayloadKey.SPONSORSHIP_NEEDED
            Regex("relocat").containsMatchIn(combined) -> AllowedPayloadKey.WILLING_TO_RELOCATE
            Regex("start[_ -]?date|when can you start").containsMatchIn(combined) -> AllowedPayloadKey.EARLIEST_START_DATE
            Regex("salary|compensation|desired[_ -]?pay|pay[_ -]?expectation").containsMatchIn(combined) -> AllowedPayloadKey.SALARY_EXPECTATION
            Regex("disabilit").containsMatchIn(combined) -> AllowedPayloadKey.EEO_DISABILITY
            Regex("\\bcity\\b").containsMatchIn(combined) && !Regex("ethnicity").containsMatchIn(combined) -> AllowedPayloadKey.CITY
            Regex("\\bstate\\b|province").containsMatchIn(combined) -> AllowedPayloadKey.STATE
            Regex("\\bzip\\b|postal").containsMatchIn(combined) -> AllowedPayloadKey.ZIP
            Regex("\\bcountry\\b").containsMatchIn(combined) -> AllowedPayloadKey.COUNTRY
            Regex("\\blocation\\b|current[_ -]?location|address").containsMatchIn(combined) -> AllowedPayloadKey.LOCATION
            else -> null
        }
    }

    private fun isProtectedKey(key: AllowedPayloadKey): Boolean = key in setOf(
        AllowedPayloadKey.WORK_AUTHORIZATION,
        AllowedPayloadKey.SPONSORSHIP_NEEDED,
        AllowedPayloadKey.SALARY_EXPECTATION,
        AllowedPayloadKey.EEO_DISABILITY,
    )

    private fun buildPayloadValueMap(
        profile: UserProfile,
        baseResume: BaseResume?,
        storedAnswers: Map<String, String>,
    ): Map<AllowedPayloadKey, String> {
        val fullName = profile.name.ifBlank { baseResume?.contact?.name.orEmpty() }.trim()
        val parts = fullName.split(Regex("\\s+"), limit = 2)
        val firstName = parts.getOrNull(0).orEmpty()
        val lastName = parts.getOrNull(1).orEmpty()
        val email = profile.email.ifBlank { baseResume?.contact?.email.orEmpty() }.trim()
        val phone = profile.phone.ifBlank { baseResume?.contact?.phone.orEmpty() }.trim()
        val location = profile.location.ifBlank { baseResume?.contact?.location.orEmpty() }.trim()
        val locParsed = CourierAgentConfig.parseLocationProfile(location)
        val github = profile.github.ifBlank { baseResume?.contact?.github.orEmpty() }.trim()
        val linkedin = baseResume?.contact?.linkedin.orEmpty().trim()

        return mapOf(
            AllowedPayloadKey.FIRST_NAME to firstName,
            AllowedPayloadKey.LAST_NAME to lastName,
            AllowedPayloadKey.FULL_NAME to fullName,
            AllowedPayloadKey.EMAIL to email,
            AllowedPayloadKey.PHONE to phone,
            AllowedPayloadKey.LOCATION to location,
            AllowedPayloadKey.CITY to locParsed.city.ifBlank { location },
            AllowedPayloadKey.STATE to locParsed.stateFull.ifBlank { locParsed.stateAbbr },
            AllowedPayloadKey.ZIP to locParsed.zip,
            AllowedPayloadKey.COUNTRY to locParsed.country,
            AllowedPayloadKey.GITHUB_URL to github,
            AllowedPayloadKey.LINKEDIN_URL to linkedin,
            AllowedPayloadKey.WEBSITE_URL to github,
            AllowedPayloadKey.WORK_AUTHORIZATION to storedAnswers["work_authorization"].orEmpty(),
            AllowedPayloadKey.SPONSORSHIP_NEEDED to storedAnswers["sponsorship_needed"].orEmpty(),
            AllowedPayloadKey.WILLING_TO_RELOCATE to storedAnswers["willing_to_relocate"].orEmpty(),
            AllowedPayloadKey.EARLIEST_START_DATE to storedAnswers["earliest_start_date"].orEmpty(),
            AllowedPayloadKey.SALARY_EXPECTATION to storedAnswers["salary_expectation"].orEmpty(),
            AllowedPayloadKey.EEO_GENDER to CourierResponseSynthesizer.normalizeGender(profile.gender),
            AllowedPayloadKey.EEO_RACE to CourierResponseSynthesizer.normalizeRace(profile.race),
            AllowedPayloadKey.EEO_VETERAN to CourierResponseSynthesizer.normalizeVeteranStatus(profile.veteranStatus),
            AllowedPayloadKey.EEO_DISABILITY to storedAnswers["eeo_disability_status"].orEmpty(),
        )
    }

    private fun findApplicantAnswerMatch(
        questionText: String,
        storedAnswers: Map<String, String>,
    ): String? {
        val normQ = GeminiCourierModel.normalizeFieldLabel(questionText)
        storedAnswers[normQ]?.takeIf { it.isNotBlank() }?.let { return it }
        return storedAnswers.entries.firstOrNull { (pattern, ans) ->
            ans.isNotBlank() && normQ.contains(GeminiCourierModel.normalizeFieldLabel(pattern))
        }?.value
    }

    private suspend fun loadCachedGitHubRepos(container: AppContainer): List<GitHubRepo> {
        val fromCacheTable = container.gitHubRepoCacheDao.getAll().map { it.toDomain() }
        if (fromCacheTable.isNotEmpty()) return fromCacheTable
        return container.gitHubRepoDao.getAll().map { it.toDomain() }
    }

    private fun valuesMatch(actual: String, expected: String): Boolean {
        val a = normalizeOptionText(actual)
        val e = normalizeOptionText(expected)
        return a.isNotBlank() && (a == e || a.contains(e) || e.contains(a))
    }

    internal fun normalizeOptionText(raw: String): String =
        raw.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractHost(url: String): String =
        runCatching { URI(url.trim()).host?.lowercase().orEmpty() }.getOrDefault("")

    private suspend fun humanPaceDelay() {
        val min = CourierAgentConfig.fieldActionDelayMinMs.coerceAtLeast(50L)
        val max = CourierAgentConfig.fieldActionDelayMaxMs.coerceAtLeast(min + 1L)
        delay(Random.nextLong(min, max))
    }
}
