package com.osrs.careerguild.core.forge

sealed class PresetInputType {
    object FreeText : PresetInputType()
    data class SingleChoice(val options: List<String>) : PresetInputType()
    data class StartDate(val quickOptions: List<String>) : PresetInputType()
}

data class PresetQuestion(
    val pattern: String,
    val label: String,
    val hint: String,
    val inputType: PresetInputType = PresetInputType.FreeText,
)

object ApplicantAnswerPresets {
    /**
     * Reusable question presets.
     * All presets start EMPTY — no default values or implied selections are ever injected.
     * Authorization and demographic fields are strictly constrained to their canonical Enums.
     */
    val PRESETS = listOf(
        PresetQuestion(
            pattern = "work_authorization",
            label = "Work Authorization (US)",
            hint = "Authorized to work in the US without restriction",
            inputType = PresetInputType.SingleChoice(YesNoOption.entries.map { it.value }),
        ),
        PresetQuestion(
            pattern = "sponsorship_needed",
            label = "Visa Sponsorship Needed",
            hint = "Will you require visa sponsorship now or in the future?",
            inputType = PresetInputType.SingleChoice(YesNoOption.entries.map { it.value }),
        ),
        PresetQuestion(
            pattern = "willing_to_relocate",
            label = "Willing to Relocate",
            hint = "Are you willing to relocate for this position?",
            inputType = PresetInputType.SingleChoice(YesNoOption.entries.map { it.value }),
        ),
        PresetQuestion(
            pattern = "earliest_start_date",
            label = "Earliest Start Date",
            hint = "Select quick availability or pick a specific date",
            inputType = PresetInputType.StartDate(StartDateOption.entries.map { it.value }),
        ),
        PresetQuestion(
            pattern = "salary_expectation",
            label = "Salary Expectation",
            hint = "e.g. $160,000 or Competitive",
            inputType = PresetInputType.FreeText,
        ),
        PresetQuestion(
            pattern = "eeo_gender",
            label = "Gender (EEO)",
            hint = "Voluntary self-identification of gender",
            inputType = PresetInputType.SingleChoice(GenderOption.entries.map { it.value }),
        ),
        PresetQuestion(
            pattern = "eeo_race_ethnicity",
            label = "Race / Ethnicity (EEO)",
            hint = "Optional — leave empty unless you choose to provide",
            inputType = PresetInputType.FreeText,
        ),
        PresetQuestion(
            pattern = "eeo_veteran_status",
            label = "Veteran Status (EEO)",
            hint = "Voluntary self-identification of protected veteran status",
            inputType = PresetInputType.SingleChoice(VeteranOption.entries.map { it.value }),
        ),
        PresetQuestion(
            pattern = "eeo_disability_status",
            label = "Disability Status (EEO)",
            hint = "Voluntary self-identification of disability status",
            inputType = PresetInputType.SingleChoice(EEOOption.entries.map { it.value }),
        ),
    )

    fun findPreset(patternOrLabel: String): PresetQuestion? {
        val clean = patternOrLabel.lowercase().trim()
        val direct = PRESETS.firstOrNull {
            it.pattern.equals(clean, ignoreCase = true) ||
                it.label.equals(clean, ignoreCase = true) ||
                clean.contains(it.pattern) ||
                clean.contains(it.label.lowercase())
        }
        if (direct != null) return direct

        // Keyword matching for ATS questions
        return when {
            Regex("authori[sz]ed to work|work authori[sz]ation|legally authori[sz]ed").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "work_authorization" }
            Regex("sponsorship|require.*visa|h-?1b").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "sponsorship_needed" }
            Regex("relocat").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "willing_to_relocate" }
            Regex("start[_ -]?date|when can you start").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "earliest_start_date" }
            Regex("salary|compensation|desired pay|pay expectation").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "salary_expectation" }
            Regex("\\bgender\\b|\\bsex\\b").containsMatchIn(clean) && !Regex("orientation").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "eeo_gender" }
            Regex("\\brace\\b|ethnic|hispanic or latino").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "eeo_race_ethnicity" }
            Regex("veteran|military status").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "eeo_veteran_status" }
            Regex("disabilit|handicap").containsMatchIn(clean) ->
                PRESETS.firstOrNull { it.pattern == "eeo_disability_status" }
            else -> null
        }
    }
}
