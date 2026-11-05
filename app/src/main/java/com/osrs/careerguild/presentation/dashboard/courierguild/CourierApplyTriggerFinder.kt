package com.osrs.careerguild.presentation.dashboard.courierguild

import java.net.URI

/**
 * Pure Kotlin Apply-Trigger Finder & Cookie Banner Dismiss Selector (Phase 2.2).
 *
 * Operates strictly on [DomControlSnapshot] lists without touching WebView or Android APIs,
 * making it 100% deterministic and unit-testable.
 */
object CourierApplyTriggerFinder {

    const val MIN_PASSING_SCORE = 30
    const val AMBIGUITY_SCORE_THRESHOLD = 10

    val KNOWN_ATS_HOSTS = listOf(
        "greenhouse.io",
        "boards.greenhouse.io",
        "job-boards.greenhouse.io",
        "lever.co",
        "jobs.lever.co",
        "ashbyhq.com",
        "jobs.ashbyhq.com",
        "workable.com",
        "apply.workable.com",
        "icims.com",
        "smartrecruiters.com",
        "myworkdayjobs.com",
        "appassets.androidplatform.net",
    )

    val URL_SHORTENER_REGEX = Regex(
        "tinyurl\\.com|bit\\.ly|t\\.co|lnkd\\.in|goo\\.gl|ow\\.ly|buff\\.ly",
        RegexOption.IGNORE_CASE
    )

    private val NEGATIVE_CONTAINER_REGEX = Regex(
        "newsletter|subscribe|job[-_ ]?alert|notify[-_ ]?me|sign[-_ ]?up[-_ ]?for[-_ ]?updates|" +
            "email[-_ ]?capture|stay[-_ ]?connected|talent[-_ ]?community|cookie|gdpr|onetrust|consent|" +
            "marketing|insights|email[-_ ]?updates",
        RegexOption.IGNORE_CASE
    )

    private val QUARANTINED_CONTAINER_REGEX = Regex(
        "quarantined|job[-_ ]?description|description|article|footer|aside|sidebar|jd[-_ ]?content|job[-_ ]?details",
        RegexOption.IGNORE_CASE
    )

    private val NEGATIVE_LABEL_OR_HREF_REGEX = Regex(
        "^mailto:|^tel:|^javascript:|tinyurl\\.com|bit\\.ly|t\\.co|lnkd\\.in|" +
            "\\bsave (this )?job\\b|\\bshare\\b|\\brefer a friend\\b|" +
            "\\bsign in\\b|\\blog in\\b|\\bcreate account\\b|\\bprivacy( policy)?\\b|" +
            "\\bterms( of (service|use))?\\b|\\bsubscribe\\b|\\bnewsletter\\b|" +
            "\\bjob alert\\b|\\bnotify me\\b|\\bsign up for updates\\b|\\bemail updates\\b|" +
            "\\binsights\\b|\\bmarketing\\b",
        RegexOption.IGNORE_CASE
    )

    private val EXACT_APPLY_LABELS = setOf(
        "apply",
        "apply now",
        "apply for this job",
        "apply for this position",
        "start application",
    )

    data class ScoredCandidate(
        val candidate: DomControlSnapshot,
        val score: Int,
        val reasons: List<String>,
    )

    data class TriggerSelectionResult(
        val formAlreadyVisible: Boolean,
        val selectedCandidate: DomControlSnapshot?,
        val scoredCandidates: List<ScoredCandidate>,
        val prunedCandidates: List<DomControlSnapshot>,
        val needsModelTieBreak: Boolean,
    )

    /**
     * Returns true if the snapshot already displays an application form (e.g. file upload input or
     * >= 2 applicant text/email/tel inputs outside newsletter/cookie containers).
     */
    fun isApplicationFormVisible(
        controls: List<DomControlSnapshot>,
        currentUrl: String = "",
    ): Boolean {
        val realInputs = controls.filter { ctrl ->
            if (NEGATIVE_CONTAINER_REGEX.containsMatchIn(ctrl.containerHint)) return@filter false
            val tag = ctrl.tag.lowercase()
            val type = ctrl.type.lowercase()
            if (tag == "input" && type == "file") return true
            (tag == "input" && type in setOf("text", "email", "tel", "")) || tag == "textarea"
        }
        if (realInputs.size >= 2) return true
        val urlLower = currentUrl.lowercase()
        if ((urlLower.endsWith("/apply") || urlLower.contains("/application")) && realInputs.isNotEmpty()) {
            return true
        }
        return false
    }

    /**
     * Locates a cookie banner dismiss action if a cookie banner is present.
     * Preference order:
     * 1. "reject non-essential", "reject all", "necessary only", "essential only", "decline"
     * 2. "close", "dismiss"
     */
    fun findCookieBannerDismiss(controls: List<DomControlSnapshot>): DomControlSnapshot? {
        val cookieControls = controls.filter { ctrl ->
            val combined = "${ctrl.containerHint} ${ctrl.id} ${ctrl.name} ${ctrl.label}".lowercase()
            Regex("cookie|consent|gdpr|onetrust|privacy[-_ ]?preference").containsMatchIn(combined) &&
                ctrl.tag.lowercase() in setOf("button", "a", "input", "div")
        }
        if (cookieControls.isEmpty()) return null

        val strictRejectRe = Regex(
            "reject non-?essential|reject all|necessary only|essential only|strictly necessary|decline|do not accept",
            RegexOption.IGNORE_CASE
        )
        cookieControls.firstOrNull { strictRejectRe.containsMatchIn(it.label) }?.let { return it }

        val closeRe = Regex("^close$|dismiss|got it", RegexOption.IGNORE_CASE)
        return cookieControls.firstOrNull { closeRe.containsMatchIn(it.label.trim()) }
    }

    /**
     * Hard-excludes newsletter traps, job alerts, mailto/tel links, auth links, legal links,
     * URL shorteners, in-prose JD links, footer/aside links, and non-ATS offsite links.
     */
    fun isHardExcluded(candidate: DomControlSnapshot, currentUrl: String = ""): Boolean {
        val tag = candidate.tag.lowercase()
        val type = candidate.type.lowercase()
        val isClickable = tag in setOf("a", "button") ||
            (tag == "input" && type in setOf("button", "submit")) ||
            candidate.containerHint.contains("button", ignoreCase = true)
        if (!isClickable) return true

        val label = candidate.label.trim()
        val href = candidate.hrefPath.trim()
        val combinedContainer = "${candidate.containerHint} ${candidate.name} ${candidate.id}"

        // 1. Hard-ban URL shorteners and mailto/tel/javascript links
        if (URL_SHORTENER_REGEX.containsMatchIn(href) || URL_SHORTENER_REGEX.containsMatchIn(label)) {
            return true
        }
        if (NEGATIVE_LABEL_OR_HREF_REGEX.containsMatchIn(label) || NEGATIVE_LABEL_OR_HREF_REGEX.containsMatchIn(href)) {
            return true
        }

        // 2. Hard-ban newsletter/marketing/subscription containers
        if (NEGATIVE_CONTAINER_REGEX.containsMatchIn(combinedContainer)) return true

        // 3. Hard-ban in-prose job description links, footers, and sidebars for <a> tags
        if (tag == "a" && QUARANTINED_CONTAINER_REGEX.containsMatchIn(combinedContainer)) {
            val isExplicitApply = EXACT_APPLY_LABELS.any { it.equals(label, ignoreCase = true) }
            if (!isExplicitApply) return true
        }

        if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
            val targetHost = extractHost(href)
            val currentHost = extractHost(currentUrl)
            if (targetHost.isNotBlank()) {
                val isSameHost = currentHost.isNotBlank() &&
                    (targetHost.equals(currentHost, ignoreCase = true) ||
                        targetHost.endsWith(".$currentHost", ignoreCase = true) ||
                        currentHost.endsWith(".$targetHost", ignoreCase = true))
                val isKnownAts = KNOWN_ATS_HOSTS.any { ats ->
                    targetHost.equals(ats, ignoreCase = true) || targetHost.endsWith(".$ats", ignoreCase = true)
                }
                if (!isSameHost && !isKnownAts) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Returns true if the control belongs to a newsletter, subscription, or marketing capture form.
     */
    fun isNewsletterOrMarketingTrap(ctrl: DomControlSnapshot): Boolean {
        val text = "${ctrl.containerHint} ${ctrl.label} ${ctrl.placeholder} ${ctrl.name} ${ctrl.id}".lowercase()
        return Regex(
            "newsletter|subscribe|job[-_ ]?alert|notify[-_ ]?me|sign[-_ ]?up[-_ ]?for[-_ ]?updates|" +
                "email[-_ ]?updates|stay[-_ ]?connected|talent[-_ ]?community|insights|marketing",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
    }

    /**
     * Scores a single non-excluded candidate for likelihood of being the primary Apply trigger.
     */
    fun scoreCandidate(candidate: DomControlSnapshot): ScoredCandidate {
        var score = 0
        val reasons = mutableListOf<String>()
        val normLabel = candidate.label.lowercase().replace(Regex("\\s+"), " ").trim()
        val hrefLower = candidate.hrefPath.lowercase().trim()
        val idNameLower = "${candidate.id} ${candidate.name}".lowercase()

        if (normLabel in EXACT_APPLY_LABELS) {
            score += 80
            reasons.add("exact_apply_label($normLabel)")
        } else if (
            normLabel.contains("apply for this job") ||
            normLabel.contains("apply for this position") ||
            normLabel.contains("apply now") ||
            normLabel.contains("start application")
        ) {
            score += 65
            reasons.add("strong_apply_phrase")
        } else if (Regex("\\bapply\\b").containsMatchIn(normLabel)) {
            score += 40
            reasons.add("contains_apply_word")
        }

        // ATS adapter apply URL patterns (Lever {job}/apply, Ashby .../application, Greenhouse #app)
        if (Regex("/[0-9a-f-]{8,}/apply\\b|/jobs/[^/]+/apply\\b|/application\\b").containsMatchIn(hrefLower)) {
            score += 55
            reasons.add("ats_adapter_apply_url")
        } else if (hrefLower.contains("/apply") || hrefLower.contains("/application") || hrefLower.contains("#app")) {
            score += 40
            reasons.add("href_apply_path")
        }

        if (idNameLower.contains("apply")) {
            score += 15
            reasons.add("id_or_name_apply")
        }

        if (candidate.tag.lowercase() in setOf("button", "a")) {
            score += 5
        }

        return ScoredCandidate(candidate = candidate, score = score, reasons = reasons)
    }

    /**
     * Selects the best Apply trigger across [controls].
     * Requests model tie-breaking ONLY when the top two candidates are within [AMBIGUITY_SCORE_THRESHOLD]
     * or when no candidate passes [MIN_PASSING_SCORE].
     */
    fun selectApplyTrigger(
        controls: List<DomControlSnapshot>,
        currentUrl: String = "",
    ): TriggerSelectionResult {
        if (isApplicationFormVisible(controls, currentUrl)) {
            return TriggerSelectionResult(
                formAlreadyVisible = true,
                selectedCandidate = null,
                scoredCandidates = emptyList(),
                prunedCandidates = emptyList(),
                needsModelTieBreak = false,
            )
        }

        val nonExcluded = controls.filterNot { isHardExcluded(it, currentUrl) }
        val scored = nonExcluded
            .map { scoreCandidate(it) }
            .filter { it.score >= MIN_PASSING_SCORE }
            .sortedByDescending { it.score }

        if (scored.isEmpty()) {
            return TriggerSelectionResult(
                formAlreadyVisible = false,
                selectedCandidate = null,
                scoredCandidates = emptyList(),
                prunedCandidates = nonExcluded,
                needsModelTieBreak = nonExcluded.isNotEmpty(),
            )
        }

        val top = scored.first()
        val second = scored.getOrNull(1)
        val isAmbiguous = second != null &&
            (top.score - second.score) <= AMBIGUITY_SCORE_THRESHOLD &&
            top.candidate.label.trim().lowercase() != second.candidate.label.trim().lowercase()

        return TriggerSelectionResult(
            formAlreadyVisible = false,
            selectedCandidate = if (isAmbiguous) null else top.candidate,
            scoredCandidates = scored,
            prunedCandidates = scored.map { it.candidate },
            needsModelTieBreak = isAmbiguous,
        )
    }

    /**
     * Derives a direct ATS adapter apply URL when the job URL belongs to a known ATS (Lever / Ashby).
     */
    fun deriveDirectAtsApplyUrl(jobUrl: String): String? {
        val trimmed = jobUrl.trim().trimEnd('/')
        val lower = trimmed.lowercase()
        return when {
            lower.contains("jobs.lever.co/") && !lower.endsWith("/apply") -> "$trimmed/apply"
            lower.contains("jobs.ashbyhq.com/") && !lower.endsWith("/application") -> "$trimmed/application"
            else -> null
        }
    }

    private fun extractHost(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            URI(url.trim()).host?.lowercase().orEmpty()
        }.getOrDefault("")
    }
}
