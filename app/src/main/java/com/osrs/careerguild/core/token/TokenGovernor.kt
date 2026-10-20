package com.osrs.careerguild.core.token

import android.content.Context
import android.content.SharedPreferences

/**
 * TokenGovernor — lightweight SharedPreferences-backed budget guard for TRAJECTORY_SCORER.
 *
 * Tracks an estimated cumulative Gemini API cost (USD) for the current calendar month.
 * If the running total exceeds [MONTHLY_BUDGET_USD] ($10), [isGeminiAllowed] returns false
 * and the scoring pipeline falls back to keyword-only Ktor search + Room cache.
 *
 * Cost model: ~$0.0001 per scored job (Gemini 1.5 Flash @ 256 output tokens ≈ $0.0001).
 * This is conservative — keeps budget well under the cap even at heavy usage.
 *
 * Storage key schema:
 *   "tg_month"       → "YYYY-MM"  (resets counters each new calendar month)
 *   "tg_cost_cents"  → Int        (accumulated cost in 1/100 cents — avoids float precision)
 *
 * Thread-safety: all reads/writes are synchronous SharedPreferences commits.
 * Safe to call from Dispatchers.IO only; never call from Main.
 *
 * Node: TRAJECTORY_SCORER (budget gate)
 */
class TokenGovernor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns true if Gemini scoring is permitted (cumulative cost < $10/month).
     * Automatically resets counters when a new calendar month is detected.
     */
    val isGeminiAllowed: Boolean
        get() {
            rolloverIfNewMonth()
            val costCents = prefs.getInt(KEY_COST_CENTS, 0)
            return costCents < MONTHLY_BUDGET_CENTS
        }

    /**
     * Record the cost of scoring [jobCount] listings.
     * Must be called after a successful Gemini scoring batch.
     *
     * @param jobCount Number of jobs scored in this batch.
     */
    fun recordScoringBatch(jobCount: Int) {
        rolloverIfNewMonth()
        val addedCents = jobCount * COST_PER_JOB_CENTS
        val current    = prefs.getInt(KEY_COST_CENTS, 0)
        prefs.edit().putInt(KEY_COST_CENTS, current + addedCents).apply()
    }

    /**
     * Estimated remaining budget this month, in USD.
     */
    val remainingBudgetUsd: Float
        get() {
            rolloverIfNewMonth()
            val spentCents = prefs.getInt(KEY_COST_CENTS, 0)
            val remaining  = (MONTHLY_BUDGET_CENTS - spentCents).coerceAtLeast(0)
            // costCents are in 1/100 cents → divide by 10_000 to get USD
            return remaining / 10_000f
        }

    /**
     * Estimated spend this month, in USD.
     */
    val spentBudgetUsd: Float
        get() {
            rolloverIfNewMonth()
            return prefs.getInt(KEY_COST_CENTS, 0) / 10_000f
        }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    /**
     * Resets cost counters when the stored month tag differs from the current month.
     * Called defensively at the start of every read/write operation.
     */
    private fun rolloverIfNewMonth() {
        val currentMonth = currentMonthTag()
        val storedMonth  = prefs.getString(KEY_MONTH, null)
        if (storedMonth != currentMonth) {
            prefs.edit()
                .putString(KEY_MONTH, currentMonth)
                .putInt(KEY_COST_CENTS, 0)
                .apply()
        }
    }

    /** Returns "YYYY-MM" for the current system date. */
    private fun currentMonthTag(): String {
        val cal = java.util.Calendar.getInstance()
        val year  = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1 // Calendar.MONTH is 0-indexed
        return "%04d-%02d".format(year, month)
    }

    companion object {
        private const val PREFS_NAME  = "token_governor"
        private const val KEY_MONTH   = "tg_month"
        private const val KEY_COST_CENTS = "tg_cost_cents"

        /** $10.00/month cap. Stored as 1/100 cents to avoid Float rounding. */
        private const val MONTHLY_BUDGET_USD   = 10.00f
        private const val MONTHLY_BUDGET_CENTS = 100_000 // $10.00 × 10_000

        /**
         * ~$0.0001 per job scored (Gemini 1.5 Flash, 256 output tokens).
         * In 1/100-cent units: 0.0001 × 10_000 = 1.
         */
        private const val COST_PER_JOB_CENTS = 1
    }
}
