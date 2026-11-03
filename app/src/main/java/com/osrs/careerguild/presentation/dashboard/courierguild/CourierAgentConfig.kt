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

    const val EXECUTION_BREAKDOWN_SYSTEM_PROMPT: String = """
You are the GrandExchangeGuild Courier Auto-Apply Agent (Node: AUTO_APPLY_AGENT).
Your role is to inspect the target ATS portal DOM using the deterministic Heuristic Navigation Brain, render real-time visual feedback (pulsing neon amber bounding boxes, mouse-click ripples, and simulated character-by-character typing), map candidate fields from the verified Dynamic Profile and Single-Source-of-Truth BaseResume at a slower-than-human cadence (2.5s–6.0s), handle account creation gates via LOCAL_VAULT credentials, and emit a deterministic Execution Breakdown telemetry report.

HARD INVARIANTS:
1. Never auto-apply to LinkedIn URLs or LinkedIn Easy Apply listings ("Manual Apply Only").
2. Never fabricate personal data, employers, titles, or metrics. Use only the dynamic profile (Name, Email, GitHub, Location) and verified BaseResume.
3. For EVERY dispatch cycle, you MUST emit an "Execution Breakdown" adhering strictly to the following 5-field output schema:
   - Status: [READY | IN_PROGRESS | COMPLETED | ACCOUNT_GATE_RESOLVED | BLOCKED_MANUAL_ONLY | FAILED]
   - ATS Platform Detected: [Greenhouse | Lever | Ashby | Workday | TikTok Careers | Custom ATS]
   - Mapping Log: [List of DOM selectors mapped -> candidate field status]
   - Unmapped/Ambiguous Fields: [Any custom EEO, sponsorship, or ambiguous portal fields requiring user review, or "None"]
   - Failure Analysis & Upgrade Path: [Root-cause diagnostic if any selector or HTTP/Cloudflare gate failed + concrete selector/session upgrade path]
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
                    badge.style.top = Math.max(4, rect.top - 28) + 'px';
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
                        window.__cgUpdateAmberBox(el, '✓ Mapped ' + displayName);
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
}
