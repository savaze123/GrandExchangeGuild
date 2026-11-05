package com.osrs.careerguild.core.resume

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Validates dates in resume experience entries:
 * 1. Checks that endDate does not precede startDate.
 * 2. Checks consistency of date formatting (standard: "Mon YYYY", e.g. "Jan 2024").
 * 3. Permits overlapping concurrent roles.
 * Never modifies dates silently — flags issues for user review.
 */
object ResumeDateValidator {

    private val MONTH_YEAR_FORMATS = listOf(
        SimpleDateFormat("MMM yyyy", Locale.US).apply { isLenient = false },
        SimpleDateFormat("MMMM yyyy", Locale.US).apply { isLenient = false },
        SimpleDateFormat("MM/yyyy", Locale.US).apply { isLenient = false },
        SimpleDateFormat("yyyy-MM", Locale.US).apply { isLenient = false },
        SimpleDateFormat("yyyy", Locale.US).apply { isLenient = false },
    )

    private val STRICT_MON_YYYY_REGEX = Regex("""^(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{4}$""")

    fun parseDate(dateStr: String?): java.util.Date? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        if (clean.equals("Present", ignoreCase = true) || clean.equals("Current", ignoreCase = true)) {
            return java.util.Date()
        }

        for (format in MONTH_YEAR_FORMATS) {
            try {
                return format.parse(clean)
            } catch (_: Exception) {}
        }
        return null
    }

    data class DateValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
    )

    fun validateExperienceDates(experiences: List<ResumeExperience>): DateValidationResult {
        val issues = mutableListOf<String>()

        for (exp in experiences) {
            val start = exp.startDate?.trim()
            val end = exp.endDate?.trim()

            // 1. Check Mon YYYY formatting consistency
            if (!start.isNullOrBlank() && !STRICT_MON_YYYY_REGEX.matches(start)) {
                issues.add("Date format in '${exp.employer}': Start date '$start' should follow 'Mon YYYY' (e.g. 'Jan 2024').")
            }
            if (!end.isNullOrBlank() && !end.equals("Present", ignoreCase = true) && !STRICT_MON_YYYY_REGEX.matches(end)) {
                issues.add("Date format in '${exp.employer}': End date '$end' should follow 'Mon YYYY' (e.g. 'Jan 2024').")
            }

            // 2. Check that endDate does not precede startDate
            if (!start.isNullOrBlank() && !end.isNullOrBlank() && !end.equals("Present", ignoreCase = true)) {
                val parsedStart = parseDate(start)
                val parsedEnd = parseDate(end)
                if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                    issues.add("Inverted dates in '${exp.employer}': End date '$end' cannot precede start date '$start'.")
                }
            }
        }

        return DateValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
        )
    }
}
