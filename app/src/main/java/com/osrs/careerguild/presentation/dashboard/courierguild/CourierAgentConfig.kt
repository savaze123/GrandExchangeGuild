package com.osrs.careerguild.presentation.dashboard.courierguild

/**
 * Configuration, System Prompt, Heuristic Navigation Brain, Real-Time Visual Overlay,
 * and Structured Telemetry Formatter for the Courier Auto-Apply Agent.
 *
 * Implements:
 * - Section 6: Execution Breakdown Prompt Injection (5-part structured schema).
 * - Instruction Update Section 2:
 *   1. Smart Heuristic Navigation Brain (cost-effective, zero-token DOM parser & portal navigator
 *      for Databricks, Stripe, OpenAI, Greenhouse, Ashby, Lever, and Workday portals).
 *   2. Real-Time Visual Overlay:
 *      - Pulsing neon amber bounding boxes (`#FFB300`) over the currently active input field or link.
 *      - Simulated character-by-character typing effects as candidate data is inputted.
 *      - Visual mouse-click ripple effects (`@keyframes cgClickRipple`) on every element interaction.
 */
object CourierAgentConfig {

    /** Maximum concurrent active workers (instruction.md §5 & Reconciliation §2). */
    var maxConcurrentWorkers: Int = 5

    /** Maximum applications submitted per rolling 24-hour day (Reconciliation §2). */
    var dailyCap: Int = 15

    /** Randomized inter-application delay bounds in milliseconds (60s–120s). */
    var interApplicationDelayMinMs: Long = 60_000L
    var interApplicationDelayMaxMs: Long = 120_000L

    /** Randomized human pacing between individual field actions (400ms–1500ms, Phase 2.6). */
    var fieldActionDelayMinMs: Long = 400L
    var fieldActionDelayMaxMs: Long = 1_500L

    /** Global default submit policy (Phase 4.1). */
    var defaultSubmitPolicy: SubmitPolicy = SubmitPolicy.REVIEW_BEFORE_SUBMIT

    const val EXECUTION_BREAKDOWN_SYSTEM_PROMPT: String = """
# Master System Prompt: GrandExchangeGuild Courier Agent v5 (Argon / Antigravity Execution)

1. Core Architecture & 4-Stage Pipeline:
   - Stage A (Discovery & Scraping): Direct ATS listings (Greenhouse, Lever, Ashby, Workday).
   - Stage B (Scoring & Gap Analysis): Compare JD against BaseResume and GitHub metrics.
   - Stage C (Tailoring Assets): Tailor bullets and grounded humble/eager answers; 1-page PDF via ResumeForge.
   - Stage D (Form Filling & Submission - HITL): Sequential DOM pipeline, pausing at final review/submit for 1-click human approval.

2. Strict Execution Sequence & DOM Scoping:
   - Step 1 (Pre-flight & Login): Guest checkouts or LOCAL_VAULT credentials.
   - Step 2 (Resume Import FIRST): Immediately attach tailored Forge resume via <input type="file"> before text inputs so native ATS parsers pre-fill fields.
   - Step 3 (Delta Contact & Profile Fill): Populate only fields left blank by the resume parser dynamically from UserProfileManager.getInstance(context).profile.
   - Step 4 (Screening & Strict EEO): Populate custom questions and demographic dropdowns using strict literal matching.

3. Strict EEO & Demographic Data Rules (Zero Hallucination):
   - Phone: UserProfileManager.getInstance(context).profile.phone
   - Gender: Decline to self-identify (or user profile setting)
   - Race / Ethnicity: Decline to self-identify (or user profile setting)
   - Veteran Status: I decline to self-identify for protected veteran status (or user profile setting)

4. Smart DOM Filtering & Accessibility Ignore List:
   - Restrict DOM scanning strictly to interactive form controls (input, select, textarea, [role="combobox"], [role="listbox"]).
   - Ignore static footer/disclaimer text containing "need assistance", "reasonable accommodation", "accessibility", or "reach out to tinyurl".
   - Only trigger CourierCaptchaQuest hard stops for verified interactive CAPTCHAs (recaptcha, hcaptcha, turnstile) or mandatory video/assessment portals (HireVue, HackerRank).

5. Concurrency & State Persistence Invariants:
   - Strict 5 concurrent workers cap (MAX_CONCURRENT_APPLICATIONS = 5).
   - All queue items, active statuses, deletion tombstones, and room view selections persist in CourierAutoApplyDispatcher across tab navigation.
"""

    data class ExecutionBreakdown(
        val status: String,
        val atsPlatformDetected: String,
        val mappingLog: List<String>,
        val unmappedAmbiguousFields: List<String>,
        val failureAnalysisAndUpgradePath: String,
    ) {
        /**
         * Formats the structured Execution Breakdown as concise console lines for the
         * `LIVE AUTOFILL LEDGER CONSOLE`.
         */
        fun toConsoleLines(company: String): List<String> {
            val mappedSummary = if (mappingLog.isEmpty()) "0 fields" else mappingLog.joinToString("; ")
            val unmappedSummary = if (unmappedAmbiguousFields.isEmpty()) "None" else unmappedAmbiguousFields.joinToString(", ")
            return listOf(
                "━━ EXECUTION BREAKDOWN [$company] ━━",
                "• Status: $status",
                "• ATS Platform Detected: $atsPlatformDetected",
                "• Mapping Log: $mappedSummary",
                "• Unmapped/Ambiguous Fields: $unmappedSummary",
                "• Failure Analysis & Upgrade Path: $failureAnalysisAndUpgradePath",
            )
        }
    }

    /**
     * Detects the ATS platform from the portal URL or fallback label.
     */
    fun detectAtsPlatform(url: String, fallbackPlatform: String = "Ashby"): String {
        val lower = url.lowercase()
        return when {
            lower.contains("linkedin") -> "LinkedIn (Excluded - Manual Only)"
            lower.contains("greenhouse") -> "Greenhouse"
            lower.contains("lever.co") || lower.contains("lever") -> "Lever"
            lower.contains("ashbyhq") || lower.contains("ashby") -> "Ashby"
            lower.contains("myworkdayjobs") || lower.contains("workday") -> "Workday"
            lower.contains("tiktok") || lower.contains("bytedance") -> "TikTok Careers"
            lower.contains("workable") -> "Workable"
            else -> fallbackPlatform.ifBlank { "Custom ATS" }
        }
    }

    private fun escapeJs(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", "")

    /**
     * Injects the Real-Time Visual Overlay CSS + DOM helpers into the WebView:
     * 1. Pulsing Neon Amber Bounding Box (`#cg-amber-bbox`) tracking the active input/link.
     * 2. Floating Neon Amber Action Badge (`#cg-amber-badge`) showing simulated typing progress.
     * 3. Visual Mouse-Click Ripple Effect (`window.__cgTriggerClickRipple(el)`) at click coordinates.
     * 4. Simulated Character-by-Character Typing (`window.__cgHeuristicFillField(...)`).
     */
    fun buildHeuristicVisualOverlayBootstrapJs(): String = """
        (function() {
            if (window.__cgOverlayBootstrapped) return "already_bootstrapped";
            window.__cgOverlayBootstrapped = true;

            const style = document.createElement('style');
            style.id = 'cg-visual-overlay-style';
            style.textContent = `
                @keyframes cgNeonAmberPulse {
                    0% {
                        box-shadow: 0 0 10px #FFB300, 0 0 22px rgba(255, 179, 0, 0.75), inset 0 0 8px rgba(255, 179, 0, 0.35);
                        border-color: #FFB300;
                        transform: scale(1.0);
                    }
                    50% {
                        box-shadow: 0 0 20px #FFD700, 0 0 36px rgba(255, 215, 0, 0.95), inset 0 0 14px rgba(255, 215, 0, 0.55);
                        border-color: #FFD700;
                        transform: scale(1.012);
                    }
                    100% {
                        box-shadow: 0 0 10px #FFB300, 0 0 22px rgba(255, 179, 0, 0.75), inset 0 0 8px rgba(255, 179, 0, 0.35);
                        border-color: #FFB300;
                        transform: scale(1.0);
                    }
                }
                @keyframes cgClickRippleEffect {
                    0% {
                        width: 10px;
                        height: 10px;
                        opacity: 1.0;
                        border-width: 4px;
                    }
                    100% {
                        width: 92px;
                        height: 92px;
                        opacity: 0.0;
                        border-width: 1px;
                    }
                }
                #cg-amber-bbox {
                    position: fixed;
                    pointer-events: none;
                    z-index: 2147483645;
                    border: 3px solid #FFB300;
                    border-radius: 8px;
                    background: rgba(255, 179, 0, 0.08);
                    animation: cgNeonAmberPulse 1.1s infinite ease-in-out;
                    transition: top 0.18s ease-out, left 0.18s ease-out, width 0.18s ease-out, height 0.18s ease-out, opacity 0.2s;
                    opacity: 0;
                }
                #cg-amber-badge {
                    position: fixed;
                    pointer-events: none;
                    z-index: 2147483646;
                    background: #1A0F08;
                    color: #FFD700;
                    border: 1.5px solid #FFB300;
                    border-radius: 6px;
                    padding: 3px 8px;
                    font-family: monospace;
                    font-size: 11px;
                    font-weight: bold;
                    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.85), 0 0 12px rgba(255, 179, 0, 0.65);
                    transition: top 0.18s ease-out, left 0.18s ease-out, opacity 0.2s;
                    opacity: 0;
                    white-space: nowrap;
                    max-width: 90vw;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .cg-click-ripple {
                    position: fixed;
                    pointer-events: none;
                    z-index: 2147483647;
                    border-radius: 50%;
                    border: 3px solid #FFB300;
                    background: rgba(255, 215, 0, 0.28);
                    box-shadow: 0 0 18px #FFB300;
                    transform: translate(-50%, -50%);
                    animation: cgClickRippleEffect 0.68s cubic-bezier(0.16, 1, 0.3, 1) forwards;
                }
            `;
            document.head.appendChild(style);

            const bbox = document.createElement('div');
            bbox.id = 'cg-amber-bbox';
            document.body.appendChild(bbox);

            const badge = document.createElement('div');
            badge.id = 'cg-amber-badge';
            document.body.appendChild(badge);

            window.__cgActiveTarget = null;

            window.__cgUpdateAmberBox = function(el, labelText) {
                if (!el) return;
                window.__cgActiveTarget = el;
                const rect = el.getBoundingClientRect();
                const pad = 4;
                bbox.style.top = Math.max(2, rect.top - pad) + 'px';
                bbox.style.left = Math.max(2, rect.left - pad) + 'px';
                bbox.style.width = Math.max(24, rect.width + pad * 2) + 'px';
                bbox.style.height = Math.max(22, rect.height + pad * 2) + 'px';
                bbox.style.opacity = '1';

                if (labelText) {
                    badge.textContent = labelText;
                    badge.style.top = Math.min(window.innerHeight - 24, rect.bottom + 8) + 'px';
                    badge.style.left = Math.max(6, rect.left) + 'px';
                    badge.style.opacity = '1';
                }
            };

            window.addEventListener('scroll', function() {
                if (window.__cgActiveTarget) {
                    window.__cgUpdateAmberBox(window.__cgActiveTarget, null);
                }
            }, { passive: true });

            window.__cgTriggerClickRipple = function(el) {
                if (!el) return;
                const rect = el.getBoundingClientRect();
                const cx = rect.left + rect.width / 2;
                const cy = rect.top + rect.height / 2;
                const ripple = document.createElement('div');
                ripple.className = 'cg-click-ripple';
                ripple.style.left = cx + 'px';
                ripple.style.top = cy + 'px';
                document.body.appendChild(ripple);
                setTimeout(function() {
                    if (ripple.parentNode) ripple.parentNode.removeChild(ripple);
                }, 720);
            };

            window.__cgFindFieldHeuristically = function(selectorList, keywordHints) {
                const candidateSelectors = selectorList.split(',').map(s => s.trim()).filter(Boolean);
                for (let i = 0; i < candidateSelectors.length; i++) {
                    const found = document.querySelector(candidateSelectors[i]);
                    if (found && found.offsetParent !== null) return found;
                }
                // Free Heuristic Brain fallback: scan all visible inputs/textareas and match label/placeholder/aria-label
                const inputs = Array.from(document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), textarea'));
                const hints = (keywordHints || []).map(k => k.toLowerCase());
                for (let idx = 0; idx < inputs.length; idx++) {
                    const inp = inputs[idx];
                    if (inp.offsetParent === null) continue;
                    let labelText = '';
                    if (inp.id) {
                        const lbl = document.querySelector('label[for="' + inp.id + '"]');
                        if (lbl) labelText += ' ' + lbl.innerText;
                    }
                    const parentLbl = inp.closest('label, .field, .form-group, .application-question');
                    if (parentLbl) labelText += ' ' + parentLbl.innerText;
                    const combined = (
                        (inp.name || '') + ' ' +
                        (inp.id || '') + ' ' +
                        (inp.placeholder || '') + ' ' +
                        (inp.getAttribute('aria-label') || '') + ' ' +
                        (inp.getAttribute('autocomplete') || '') + ' ' +
                        labelText
                    ).toLowerCase();
                    for (let h = 0; h < hints.length; h++) {
                        if (combined.indexOf(hints[h]) !== -1) {
                            return inp;
                        }
                    }
                }
                return null;
            };

            window.__cgHeuristicTypeField = function(selectorList, keywordHints, value, displayName) {
                if (!value) return "skipped";
                const el = window.__cgFindFieldHeuristically(selectorList, keywordHints);
                if (!el) return "not_found";

                // Check if native ATS resume parser already pre-filled this field
                if (el.value && el.value.trim().length > 0) {
                    window.__cgUpdateAmberBox(el, '✓ PRE-FILLED [' + displayName + ']');
                    return "prefilled";
                }

                try {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                } catch (e) {}

                window.__cgTriggerClickRipple(el);
                window.__cgUpdateAmberBox(el, '⚡ CLICK & TYPE [' + displayName + ']');
                el.focus();
                el.value = '';

                const chars = Array.from(value);
                let idx = 0;
                const stepMs = Math.max(18, Math.min(45, Math.floor(900 / Math.max(1, chars.length))));
                if (window.__cgTypingTimer) clearInterval(window.__cgTypingTimer);

                window.__cgTypingTimer = setInterval(function() {
                    if (idx < chars.length) {
                        el.value += chars[idx];
                        idx++;
                        const preview = el.value.length > 28 ? el.value.substring(0, 28) + '…' : el.value;
                        window.__cgUpdateAmberBox(el, '⌨️ Typing ' + displayName + ': "' + preview + '▋"');
                        el.dispatchEvent(new Event('keydown', { bubbles: true }));
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('keyup', { bubbles: true }));
                    } else {
                        clearInterval(window.__cgTypingTimer);
                        window.__cgTypingTimer = null;
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        window.__cgUpdateAmberBox(el, '✓ ' + displayName);
                        setTimeout(function() { badge.style.opacity = '0'; }, 1400);
                    }
                }, stepMs);

                return "typing_started";
            };

            return "bootstrapped";
        })();
    """.trimIndent()

    /**
     * Smart Heuristic Portal Navigation Brain (Zero-AI-cost):
     * Inspects the loaded web page (such as Databricks Greenhouse board, Stripe, OpenAI Ashby, etc.).
     * - If form inputs are already visible, returns `"FORM_READY"`.
     * - If on a job board index or job description page without form inputs, heuristically locates
     *   either an `"Apply"` button or the best-matching job posting link, highlights it with the
     *   pulsing neon amber bounding box, triggers the visual mouse-click ripple effect, and navigates
     *   into the application form.
     */
    fun buildHeuristicPortalNavigatorJs(targetTitle: String): String {
        val safeTitle = escapeJs(targetTitle.lowercase())
        return """
            (function() {
                const visibleInputs = Array.from(document.querySelectorAll(
                    'input[type="text"], input[type="email"], input[type="tel"], input[name*="name"], input[name*="email"], textarea'
                )).filter(el => el.offsetParent !== null);

                if (visibleInputs.length >= 2) {
                    return "FORM_READY:" + visibleInputs.length;
                }

                // 1. Check for an explicit "Apply" / "Apply for this job" button or anchor on a JD page
                const clickables = Array.from(document.querySelectorAll('a, button, input[type="button"], input[type="submit"], [role="button"]'))
                    .filter(el => el.offsetParent !== null);

                for (let i = 0; i < clickables.length; i++) {
                    const el = clickables[i];
                    const txt = (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().toLowerCase();
                    if (txt === 'apply' || txt === 'apply now' || txt === 'apply for this job' || txt === 'apply for this position' || txt.indexOf('apply for this') !== -1) {
                        try { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) {}
                        if (window.__cgTriggerClickRipple) window.__cgTriggerClickRipple(el);
                        if (window.__cgUpdateAmberBox) window.__cgUpdateAmberBox(el, '🧠 Heuristic Brain: Clicking "' + (el.innerText || 'Apply').trim() + '"');
                        setTimeout(function() { el.click(); }, 550);
                        return "CLICKED_APPLY_BUTTON:" + txt;
                    }
                }

                // 2. If on a company job board index (e.g. boards.greenhouse.io/databricks, jobs.ashbyhq.com/openai), heuristically score job links
                const ignoredWords = ['privacy', 'terms', 'cookie', 'legal', 'security', 'powered by', 'sign in', 'log in', 'linkedin', 'twitter'];
                const uuidRegex = /[0-9a-f]{8}-[0-9a-f]{4}/i;
                const links = Array.from(document.querySelectorAll('a[href]')).filter(a => {
                    if (a.offsetParent === null) return false;
                    const href = (a.getAttribute('href') || '').toLowerCase();
                    const text = (a.innerText || '').trim();
                    const lowerText = text.toLowerCase();
                    if (text.length < 6) return false;
                    for (let ig = 0; ig < ignoredWords.length; ig++) {
                        if (lowerText.indexOf(ignoredWords[ig]) !== -1 || href.indexOf(ignoredWords[ig]) !== -1) {
                            return false;
                        }
                    }
                    return href.indexOf('/jobs/') !== -1 ||
                           href.indexOf('gh_jid=') !== -1 ||
                           uuidRegex.test(href) ||
                           a.closest('.opening, tr.job-post, [data-mapped="true"], .job-posting, [class*="job"]') !== null;
                });

                if (links.length > 0) {
                    const targetWords = "$safeTitle".split(/\s+/).filter(w => w.length > 2);
                    const domainKeywords = ['engineer', 'software', 'android', 'systems', 'backend', 'data', 'ai', 'infrastructure', 'platform', 'developer', 'research', 'cloud', 'fullstack', 'full-stack'];
                    let bestLink = links[0];
                    let bestScore = -1;

                    for (let i = 0; i < links.length; i++) {
                        const lnk = links[i];
                        const t = (lnk.innerText || '').toLowerCase();
                        let score = 0;
                        for (let w = 0; w < targetWords.length; w++) {
                            if (t.indexOf(targetWords[w]) !== -1) score += 15;
                        }
                        for (let k = 0; k < domainKeywords.length; k++) {
                            if (t.indexOf(domainKeywords[k]) !== -1) score += 8;
                        }
                        if (score > bestScore) {
                            bestScore = score;
                            bestLink = lnk;
                        }
                    }

                    const chosenTitle = (bestLink.innerText || 'Job Posting').trim().split('\n')[0];
                    try { bestLink.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) {}
                    if (window.__cgTriggerClickRipple) window.__cgTriggerClickRipple(bestLink);
                    if (window.__cgUpdateAmberBox) window.__cgUpdateAmberBox(bestLink, '🧠 Heuristic Brain: Navigating to "' + chosenTitle.substring(0, 36) + '"');
                    setTimeout(function() {
                        bestLink.click();
                    }, 700);
                    return "NAVIGATING_TO_POSTING:" + chosenTitle;
                }

                return "NO_FORM_OR_POSTING_FOUND";
            })();
        """.trimIndent()
    }

    /**
     * Generates the JS call to highlight the matched field with a pulsing neon amber bounding box,
     * trigger a visual click ripple, and type the value character-by-character.
     */
    fun buildVisualSimulatedTypingJs(
        selector: String,
        keywordHints: List<String>,
        fieldValue: String,
        displayName: String,
    ): String {
        val safeVal = escapeJs(fieldValue)
        val safeSelector = escapeJs(selector)
        val safeDisplay = escapeJs(displayName)
        val hintsJson = keywordHints.joinToString(",") { "\"${escapeJs(it)}\"" }
        return """
            (function() {
                if (typeof window.__cgHeuristicTypeField === 'function') {
                    return window.__cgHeuristicTypeField("$safeSelector", [$hintsJson], "$safeVal", "$safeDisplay");
                }
                return "overlay_not_ready";
            })();
        """.trimIndent()
    }

    /**
     * Concise, human-sounding 1–2 sentence answer for open-ended questions.
     * Grounded only in the dynamic profile / BaseResume data passed in; no AI-isms, no fluff.
     */
    fun buildHumanConciseResponse(
        company: String,
        title: String,
        employer: String = "",
        skills: String = "",
    ): String {
        val topSkills = skills.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(3)
        val skillPhrase = when (topSkills.size) {
            0 -> ""
            1 -> topSkills[0]
            2 -> "${topSkills[0]} and ${topSkills[1]}"
            else -> "${topSkills[0]}, ${topSkills[1]} and ${topSkills[2]}"
        }
        val first = when {
            employer.isNotBlank() && skillPhrase.isNotBlank() ->
                "I build $skillPhrase systems at $employer and enjoy shipping practical, reliable software."
            skillPhrase.isNotBlank() -> "I work mostly with $skillPhrase and enjoy shipping practical, reliable software."
            else -> "I enjoy shipping practical, reliable software."
        }
        val target = if (title.isNotBlank()) "the $title role at $company" else "the role at $company"
        return "$first $target looks like a strong fit for that work."
    }

    /** Parsed location pieces used to fill Country / State / City / ZIP controls. */
    data class LocationProfile(
        val city: String,
        val stateFull: String,
        val stateAbbr: String,
        val zip: String,
        val country: String = "United States",
    )

    /** instruction.md §8 EEO & Profile Configuration */
    data class EeoProfile(
        val phone: String = "",
        val gender: String = CourierResponseSynthesizer.DEFAULT_GENDER,
        val race: String = CourierResponseSynthesizer.DEFAULT_RACE,
        val veteranStatus: String = CourierResponseSynthesizer.DEFAULT_VETERAN_STATUS,
    ) {
        val effectiveGender: String
            get() = CourierResponseSynthesizer.normalizeGender(gender)
        val effectiveRace: String
            get() = CourierResponseSynthesizer.normalizeRace(race)
        val effectiveVeteranStatus: String
            get() = CourierResponseSynthesizer.normalizeVeteranStatus(veteranStatus)
    }

    private val stateNames = mapOf(
        "NY" to "New York", "NJ" to "New Jersey", "CA" to "California", "TX" to "Texas",
        "FL" to "Florida", "MA" to "Massachusetts", "PA" to "Pennsylvania", "WA" to "Washington",
        "IL" to "Illinois", "CT" to "Connecticut", "VA" to "Virginia", "GA" to "Georgia",
        "NC" to "North Carolina", "CO" to "Colorado", "OH" to "Ohio", "MD" to "Maryland",
    )

    /** Parses "Valley Stream, NY 11581" / "Valley Stream, 11581" (ZIP prefix hints state if missing). */
    fun parseLocationProfile(location: String): LocationProfile {
        val zip = Regex("\\b\\d{5}\\b").find(location)?.value.orEmpty()
        val abbr = Regex("\\b([A-Z]{2})\\b").findAll(location).map { it.value }.firstOrNull { it in stateNames }
            ?: if (zip.startsWith("1")) "NY" else ""
        val city = location.substringBefore(",").replace(Regex("\\d{5}"), "").trim()
        return LocationProfile(city, stateNames[abbr].orEmpty(), abbr, zip)
    }

    /**
     * Best-effort answering pass: location controls, Yes/No toggle groups, acknowledgement
     * checkboxes, EEO dropdowns/radios (instruction.md §8), and selects. Runs as a throttled async chain inside the page.
     * Returns the estimated step count so the caller can wait.
     */
    fun buildBestEffortAnswerJs(
        loc: LocationProfile,
        yearsExperience: String = "3",
        eeo: EeoProfile = EeoProfile(),
    ): String {
        val c = "{city:\"${escapeJs(loc.city)}\",state:\"${escapeJs(loc.stateFull)}\",abbr:\"${escapeJs(loc.stateAbbr)}\"," +
            "zip:\"${escapeJs(loc.zip)}\",country:\"${escapeJs(loc.country)}\",years:\"${escapeJs(yearsExperience)}\"," +
            "phone:\"${escapeJs(eeo.phone)}\",gender:\"${escapeJs(eeo.effectiveGender)}\",race:\"${escapeJs(eeo.effectiveRace)}\",veteran:\"${escapeJs(eeo.effectiveVeteranStatus)}\"}"
        return """
            (function() {
                const ctx = $c;
                const steps = [];
                const txt = e => ((e && (e.innerText || e.textContent)) || '').trim();
                const norm = s => (s || '').toLowerCase().replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
                const vis = e => e && e.offsetParent !== null && !e.closest('footer, [role="contentinfo"]');
                const isDisclaimer = s => /need assistance|reasonable accommodation|accessibility|reach out to tinyurl/i.test(s || '');
                const aliases = { 'us': 'united states', 'usa': 'united states', 'ny': 'new york' };
                function fuzzyMatch(opt, want) {
                    let o = norm(opt), w = norm(want);
                    if (!w) return false;
                    o = aliases[o] || o; w = aliases[w] || w;
                    return o === w || o.indexOf(w) !== -1 || (o.length > 2 && w.indexOf(o) !== -1);
                }
                function questionFor(el) {
                    let n = el;
                    for (let i = 0; i < 5 && n; i++) {
                        let p = n.previousElementSibling;
                        while (p) {
                            const t = txt(p);
                            if (t.length > 8 && !/^(yes|no)$/i.test(t) && !isDisclaimer(t)) return t;
                            p = p.previousElementSibling;
                        }
                        n = n.parentElement;
                    }
                    return '';
                }
                function yesNoFor(q) {
                    q = q.toLowerCase();
                    if (/sponsor|visa/.test(q)) return 'No';
                    if (/previously (been )?employed|former employee|government official|non-?compete|conflict of interest|restrictive covenant|criminal|felony/.test(q)) return 'No';
                    if (/authori[sz]ed|legally|eligible|right to work|willing|able to|relocat|hybrid|on-?site|in-?office|office|commute|at least 18|over 18|background check|agree|consent|acknowledge/.test(q)) return 'Yes';
                    return null;
                }
                function locationValueFor(q) {
                    q = q.toLowerCase();
                    if (/country/.test(q)) return ctx.country;
                    if (/state|province|region/.test(q)) return ctx.state;
                    if (/zip|postal/.test(q)) return ctx.zip;
                    if (/city|town|currently located|where are you (located|based)/.test(q)) return ctx.city + ', ' + ctx.abbr;
                    return null;
                }
                function eeoValueFor(q) {
                    q = q.toLowerCase();
                    if (/\bgender\b|\bsex\b/.test(q) && !/orientation/.test(q)) {
                        return { kind: 'gender', target: ctx.gender };
                    }
                    if (/\brace\b|ethnic|demographic/.test(q)) {
                        return { kind: 'race', target: ctx.race };
                    }
                    if (/veteran|military/.test(q)) {
                        return { kind: 'veteran', target: ctx.veteran };
                    }
                    return null;
                }
                function matchEeoOption(optText, kind, target) {
                    const o = norm(optText);
                    const t = norm(target);
                    if (!o || !t) return false;
                    const isDeclineTarget = /decline|prefer not|choose not|do not wish|not specify|not disclose/.test(t);
                    const isDeclineOption = /decline|prefer not|choose not|do not wish|not specify|not disclose|rather not say/.test(o);
                    if (isDeclineTarget || isDeclineOption) {
                        return isDeclineTarget && isDeclineOption;
                    }
                    if (kind === 'gender') {
                        if (t === 'male') return (o === 'male' || o === 'man') && !/female|woman/.test(o);
                        if (t === 'female') return o === 'female' || o === 'woman';
                        return o === t;
                    }
                    if (kind === 'race') {
                        if (t.indexOf('hispanic') !== -1 || t.indexOf('latino') !== -1) {
                            return /hispanic|latino|latina|latinx/.test(o);
                        }
                        if (t === 'white') {
                            return (o === 'white' || o === 'caucasian' || o.indexOf('white') !== -1) && !/non white|not white|black|hispanic|asian|two or more|multi/.test(o);
                        }
                        if (t === 'black') {
                            return /black|african american/.test(o);
                        }
                        if (t.indexOf('pacific') !== -1 || t.indexOf('hawaiian') !== -1) {
                            return /native hawaiian|pacific islander/.test(o);
                        }
                        if (t === 'asian') {
                            return (o === 'asian' || o.indexOf('asian') !== -1) && !/caucasian/.test(o);
                        }
                        if (t.indexOf('american indian') !== -1 || t.indexOf('alaska native') !== -1) {
                            return /american indian|alaska native|indigenous|native american/.test(o);
                        }
                        if (t.indexOf('two or more') !== -1) {
                            return /two or more|multi/.test(o);
                        }
                        return o === t;
                    }
                    if (kind === 'veteran') {
                        if (t.indexOf('not a protected veteran') !== -1 || t === 'i am not a protected veteran') {
                            return (/not a protected veteran|not a veteran|am not|no\b/.test(o)) && !isDeclineOption;
                        }
                        if (t.indexOf('identify as a veteran') !== -1 || t === 'i identify as a veteran') {
                            return (/identify as.*veteran|protected veteran|i am a veteran|yes\b/.test(o)) && !/not/.test(o) && !isDeclineOption;
                        }
                        return o === t;
                    }
                    return false;
                }
                function click(el) {
                    try { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) {}
                    if (window.__cgTriggerClickRipple) window.__cgTriggerClickRipple(el);
                    if (window.__cgUpdateAmberBox) window.__cgUpdateAmberBox(el, null);
                    el.click();
                }
                function setNative(el, v) {
                    const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                // 1. Yes/No toggle button groups
                const groups = new Map();
                document.querySelectorAll('button, [role="button"]').forEach(b => {
                    if (!vis(b) || !/^(yes|no)${'$'}/i.test(txt(b))) return;
                    const key = b.parentElement;
                    if (!groups.has(key)) groups.set(key, []);
                    groups.get(key).push(b);
                });
                groups.forEach((btns, parent) => {
                    const pressed = btns.some(b => b.getAttribute('aria-pressed') === 'true' || /active|selected|checked/.test(b.className));
                    if (pressed) return;
                    const q = questionFor(parent);
                    if (!q || isDisclaimer(q)) return;
                    const ans = yesNoFor(q);
                    if (!ans) { steps.push({ skip: q.substring(0, 60) }); return; }
                    const btn = btns.find(b => txt(b).toLowerCase() === ans.toLowerCase());
                    if (btn) steps.push({ run: () => click(btn), label: ans + ' -> ' + q.substring(0, 40) });
                });
                // 2. Native selects (EEO + Location + Yes/No)
                document.querySelectorAll('select').forEach(sel => {
                    if (!vis(sel)) return;
                    const q = questionFor(sel) + ' ' + (sel.name || '') + ' ' + (sel.id || '');
                    if (isDisclaimer(q)) return;
                    const eeo = eeoValueFor(q);
                    if (eeo) {
                        const opt = Array.from(sel.options).find(o => matchEeoOption(txt(o), eeo.kind, eeo.target) || matchEeoOption(o.value, eeo.kind, eeo.target));
                        if (opt) {
                            steps.push({
                                run: () => {
                                    sel.value = opt.value;
                                    sel.dispatchEvent(new Event('input', { bubbles: true }));
                                    sel.dispatchEvent(new Event('change', { bubbles: true }));
                                },
                                label: 'eeo select [' + eeo.kind + '] -> ' + opt.text
                            });
                            return;
                        }
                    }
                    if (sel.selectedIndex > 0) return;
                    let want = locationValueFor(q);
                    if (!want) { const yn = yesNoFor(q); if (yn) want = yn; }
                    if (!want) return;
                    const opt = Array.from(sel.options).find(o => fuzzyMatch(o.text, want) || fuzzyMatch(o.value, want) || (want === ctx.state && fuzzyMatch(o.text, ctx.abbr)));
                    if (opt) steps.push({ run: () => { sel.value = opt.value; sel.dispatchEvent(new Event('change', { bubbles: true })); }, label: 'select ' + opt.text });
                });
                // 2b. Radio button groups (EEO & Yes/No)
                const radioGroups = new Map();
                document.querySelectorAll('input[type="radio"]').forEach(r => {
                    if (!vis(r)) return;
                    const container = r.closest('fieldset, .form-group, .field, [role="radiogroup"], div') || r.parentElement;
                    if (!radioGroups.has(container)) radioGroups.set(container, []);
                    radioGroups.get(container).push(r);
                });
                radioGroups.forEach((radios, container) => {
                    const q = questionFor(container) + ' ' + (container.getAttribute('aria-label') || '');
                    if (isDisclaimer(q)) return;
                    const eeo = eeoValueFor(q);
                    if (eeo) {
                        const match = radios.find(r => {
                            let label = txt(r.parentElement) || r.value || '';
                            if (r.id) {
                                const lbl = document.querySelector('label[for="' + r.id + '"]');
                                if (lbl) label += ' ' + txt(lbl);
                            }
                            return matchEeoOption(label, eeo.kind, eeo.target);
                        });
                        if (match) {
                            steps.push({ run: () => click(match), label: 'eeo radio [' + eeo.kind + '] -> ' + eeo.target });
                            return;
                        }
                    }
                    const isChecked = radios.some(r => r.checked);
                    if (isChecked) return;
                    const yn = yesNoFor(q);
                    if (yn) {
                        const match = radios.find(r => {
                            const label = (txt(r.parentElement) || r.value || '').toLowerCase();
                            return yn.toLowerCase() === 'yes' ? /^(yes|true)$/.test(label) : /^(no|false)$/.test(label);
                        });
                        if (match) {
                            steps.push({ run: () => click(match), label: yn + ' radio -> ' + q.substring(0, 40) });
                        }
                    }
                });
                // 2c. Custom dropdowns & ARIA select menus (strictly interactive [role="combobox"], [role="listbox"])
                document.querySelectorAll('[role="combobox"], [role="listbox"]').forEach(dd => {
                    if (!vis(dd)) return;
                    const q = questionFor(dd) + ' ' + (dd.getAttribute('aria-label') || '') + ' ' + (dd.id || '');
                    if (isDisclaimer(q)) return;
                    const eeo = eeoValueFor(q);
                    if (eeo) {
                        steps.push({
                            run: () => {
                                click(dd);
                                setTimeout(() => {
                                    const options = Array.from(document.querySelectorAll('[role="option"], .select__option, li[data-value], div[class*="option"]'));
                                    const match = options.find(o => matchEeoOption(txt(o), eeo.kind, eeo.target));
                                    if (match) click(match);
                                }, 300);
                            },
                            label: 'custom eeo dropdown [' + eeo.kind + '] -> ' + eeo.target
                        });
                    }
                });
                // 3. Phone inputs (instruction.md §8: strictly dynamically injected from profile.phone)
                if (ctx.phone) {
                    document.querySelectorAll('input[type="tel"], input[name*="phone"], input[id*="phone"], input[autocomplete*="tel"]').forEach(inp => {
                        if (!vis(inp)) return;
                        if (!inp.value || inp.value.trim().length === 0) {
                            steps.push({
                                run: () => {
                                    inp.focus();
                                    setNative(inp, ctx.phone);
                                },
                                label: 'phone ' + ctx.phone
                            });
                        }
                    });
                }
                // 3b. Text input EEO fields (if text-based instead of dropdown)
                document.querySelectorAll('input[type="text"], input:not([type])').forEach(inp => {
                    if (!vis(inp)) return;
                    const q = questionFor(inp.parentElement || inp) + ' ' + (inp.getAttribute('aria-label') || '') + ' ' + (inp.placeholder || '') + ' ' + (inp.name || '');
                    const eeo = eeoValueFor(q);
                    if (eeo && (!inp.value || inp.value.trim().length === 0)) {
                        steps.push({
                            run: () => {
                                inp.focus();
                                setNative(inp, eeo.target);
                            },
                            label: 'eeo text [' + eeo.kind + '] -> ' + eeo.target
                        });
                    }
                });
                // 3c. Location text inputs / comboboxes
                document.querySelectorAll('input[type="text"], input:not([type]), input[role="combobox"], input[aria-autocomplete]').forEach(inp => {
                    if (!vis(inp) || inp.value) return;
                    const q = questionFor(inp.parentElement || inp) + ' ' + (inp.getAttribute('aria-label') || '') + ' ' + (inp.placeholder || '') + ' ' + (inp.name || '');
                    const want = locationValueFor(q);
                    if (want) steps.push({ run: () => { inp.focus(); setNative(inp, want); }, label: 'location ' + want });
                });
                // 4. Acknowledgement / agreement checkboxes
                document.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                    if (cb.checked) return;
                    const ctxText = txt(cb.closest('label, div, fieldset')).toLowerCase();
                    if (/acknowledg|agree|certify|consent|privacy|arbitration|terms/.test(ctxText)) {
                        steps.push({ run: () => click(cb), label: 'acknowledge' });
                    }
                });
                // Throttled execution (2.5s - 6s between fields)
                let i = 0;
                function next() {
                    if (i >= steps.length) { window.__cgBestEffortDone = true; return; }
                    const s = steps[i++];
                    try { if (s.run) s.run(); } catch (e) {}
                    setTimeout(next, 2500 + Math.random() * 3500);
                }
                window.__cgBestEffortDone = false;
                next();
                return "STEPS:" + steps.filter(s => s.run).length + ";SKIPPED:" + steps.filter(s => s.skip).map(s => s.skip).join('|');
            })();
        """.trimIndent()
    }
}
