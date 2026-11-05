package com.osrs.careerguild.core.forge

data class PresetQuestion(
    val pattern: String,
    val label: String,
    val hint: String,
)

object ApplicantAnswerPresets {
    /**
     * Reusable question presets.
     * All presets start EMPTY — no default values or implied selections are ever injected.
     */
    val PRESETS = listOf(
        PresetQuestion(
            pattern = "work_authorization",
            label = "Work Authorization (US)",
            hint = "e.g. Yes / Authorized to work in the US",
        ),
        PresetQuestion(
            pattern = "sponsorship_needed",
            label = "Visa Sponsorship Needed",
            hint = "e.g. No / Will not require sponsorship",
        ),
        PresetQuestion(
            pattern = "willing_to_relocate",
            label = "Willing to Relocate",
            hint = "e.g. Yes / No",
        ),
        PresetQuestion(
            pattern = "earliest_start_date",
            label = "Earliest Start Date",
            hint = "e.g. 2 weeks notice / Immediate",
        ),
        PresetQuestion(
            pattern = "salary_expectation",
            label = "Salary Expectation",
            hint = "e.g. $160,000 or Competitive",
        ),
        PresetQuestion(
            pattern = "eeo_gender",
            label = "Gender (EEO)",
            hint = "Optional — leave empty unless you choose to provide",
        ),
        PresetQuestion(
            pattern = "eeo_race_ethnicity",
            label = "Race / Ethnicity (EEO)",
            hint = "Optional — leave empty unless you choose to provide",
        ),
        PresetQuestion(
            pattern = "eeo_veteran_status",
            label = "Veteran Status (EEO)",
            hint = "Optional — leave empty unless you choose to provide",
        ),
        PresetQuestion(
            pattern = "eeo_disability_status",
            label = "Disability Status (EEO)",
            hint = "Optional — leave empty unless you choose to provide",
        ),
    )
}
