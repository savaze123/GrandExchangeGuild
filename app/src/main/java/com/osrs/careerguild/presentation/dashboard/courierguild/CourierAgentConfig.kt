package com.osrs.careerguild.presentation.dashboard.courierguild

/**
 * Configuration, System Prompt, and Structured Telemetry Formatter for the Courier Auto-Apply Agent.
 *
 * Implements Section 6: Execution Breakdown Prompt Injection.
 * Enforces the 5-part structured output schema routed directly into the LIVE AUTOFILL LEDGER CONSOLE:
 *  - `Status`
 *  - `ATS Platform Detected`
 *  - `Mapping Log`
 *  - `Unmapped/Ambiguous Fields`
 *  - `Failure Analysis & Upgrade Path`
 */
object CourierAgentConfig {

    const val EXECUTION_BREAKDOWN_SYSTEM_PROMPT: String = """
You are the GrandExchangeGuild Courier Auto-Apply Agent (Node: AUTO_APPLY_AGENT).
Your role is to inspect the target ATS portal DOM, map candidate fields from the verified Dynamic Profile and Single-Source-of-Truth BaseResume at a slower-than-human cadence (2.5s–6.0s), handle account creation gates via LOCAL_VAULT credentials, and emit a deterministic Execution Breakdown telemetry report.

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
}
