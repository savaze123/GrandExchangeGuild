package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.osrs.careerguild.core.forge.EEOOption
import com.osrs.careerguild.core.forge.GenderOption
import com.osrs.careerguild.core.forge.StartDateOption
import com.osrs.careerguild.core.forge.VeteranOption
import com.osrs.careerguild.core.forge.YesNoOption

/**
 * Reusable applicant answers store encrypted in SQLCipher Room DB.
 * Maps question pattern strings to the candidate's verified answers.
 * Presets start completely empty and are only populated when explicitly set by the user.
 *
 * Strict selection fields (authorization, sponsorship, relocation, gender, veteran,
 * disability, earliest start date) are backed by canonical Enums ([YesNoOption],
 * [EEOOption], [GenderOption], [VeteranOption], [StartDateOption]) stored via their String equivalents.
 */
@Entity(tableName = "applicant_answers")
data class ApplicantAnswerEntity(
    @PrimaryKey
    @ColumnInfo(name = "question_pattern")
    val questionPattern: String,
    @ColumnInfo(name = "answer")
    val answer: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    // Typed Enum accessors (non-column getters)
    val yesNoOption: YesNoOption?
        get() = YesNoOption.fromString(answer)

    val eeoOption: EEOOption?
        get() = EEOOption.fromString(answer)

    val genderOption: GenderOption?
        get() = GenderOption.fromString(answer)

    val veteranOption: VeteranOption?
        get() = VeteranOption.fromString(answer)

    val startDateOption: StartDateOption?
        get() = StartDateOption.fromString(answer)

    companion object {
        const val PATTERN_WORK_AUTHORIZATION = "work_authorization"
        const val PATTERN_SPONSORSHIP_NEEDED = "sponsorship_needed"
        const val PATTERN_WILLING_TO_RELOCATE = "willing_to_relocate"
        const val PATTERN_EARLIEST_START_DATE = "earliest_start_date"
        const val PATTERN_SALARY_EXPECTATION = "salary_expectation"
        const val PATTERN_EEO_GENDER = "eeo_gender"
        const val PATTERN_EEO_RACE = "eeo_race_ethnicity"
        const val PATTERN_EEO_VETERAN = "eeo_veteran_status"
        const val PATTERN_EEO_DISABILITY = "eeo_disability_status"

        fun workAuthorization(option: YesNoOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_WORK_AUTHORIZATION, option.value)

        fun sponsorshipNeeded(option: YesNoOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_SPONSORSHIP_NEEDED, option.value)

        fun willingToRelocate(option: YesNoOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_WILLING_TO_RELOCATE, option.value)

        fun relocate(option: YesNoOption): ApplicantAnswerEntity =
            willingToRelocate(option)

        fun gender(option: GenderOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_EEO_GENDER, option.value)

        fun veteranStatus(option: VeteranOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_EEO_VETERAN, option.value)

        fun veteran(option: VeteranOption): ApplicantAnswerEntity =
            veteranStatus(option)

        fun disabilityStatus(option: EEOOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_EEO_DISABILITY, option.value)

        fun disability(option: EEOOption): ApplicantAnswerEntity =
            disabilityStatus(option)

        fun earliestStartDate(option: StartDateOption): ApplicantAnswerEntity =
            ApplicantAnswerEntity(PATTERN_EARLIEST_START_DATE, option.value)

        fun startDate(option: StartDateOption): ApplicantAnswerEntity =
            earliestStartDate(option)
    }
}
