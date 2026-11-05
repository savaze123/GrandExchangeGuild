package com.osrs.careerguild.core.forge

import com.osrs.careerguild.data.local.entity.ApplicantAnswerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicantAnswerEnumsTest {

    @Test
    fun yesNoOption_fromString_parsesCorrectly() {
        assertEquals(YesNoOption.YES, YesNoOption.fromString("Yes"))
        assertEquals(YesNoOption.YES, YesNoOption.fromString("yes"))
        assertEquals(YesNoOption.YES, YesNoOption.fromString("TRUE"))
        assertEquals(YesNoOption.YES, YesNoOption.fromString("y"))
        assertEquals(YesNoOption.NO, YesNoOption.fromString("No"))
        assertEquals(YesNoOption.NO, YesNoOption.fromString("no"))
        assertEquals(YesNoOption.NO, YesNoOption.fromString("FALSE"))
        assertEquals(YesNoOption.NO, YesNoOption.fromString("n"))
        assertNull(YesNoOption.fromString("maybe"))
        assertNull(YesNoOption.fromString(null))
    }

    @Test
    fun eeoOption_fromString_parsesCorrectly() {
        assertEquals(EEOOption.YES, EEOOption.fromString("Yes"))
        assertEquals(EEOOption.NO, EEOOption.fromString("No"))
        assertEquals(EEOOption.PREFER_NOT_TO_SAY, EEOOption.fromString("Prefer not to say"))
        assertEquals(EEOOption.PREFER_NOT_TO_SAY, EEOOption.fromString("decline to self-identify"))
        assertEquals(EEOOption.PREFER_NOT_TO_SAY, EEOOption.fromString("i choose not to disclose"))
        assertNull(EEOOption.fromString("other"))
    }

    @Test
    fun genderOption_fromString_parsesCorrectly() {
        assertEquals(GenderOption.MALE, GenderOption.fromString("Male"))
        assertEquals(GenderOption.MALE, GenderOption.fromString("man"))
        assertEquals(GenderOption.FEMALE, GenderOption.fromString("Female"))
        assertEquals(GenderOption.FEMALE, GenderOption.fromString("woman"))
        assertEquals(GenderOption.NON_BINARY, GenderOption.fromString("Non-binary"))
        assertEquals(GenderOption.NON_BINARY, GenderOption.fromString("nonbinary"))
        assertEquals(GenderOption.PREFER_NOT_TO_SAY, GenderOption.fromString("Prefer not to say"))
        assertEquals(GenderOption.PREFER_NOT_TO_SAY, GenderOption.fromString("decline to self-identify"))
        assertEquals(GenderOption.PREFER_NOT_TO_SAY, GenderOption.fromString("do not wish to answer"))
        assertNull(GenderOption.fromString("alien"))
    }

    @Test
    fun veteranOption_fromString_parsesCorrectly() {
        assertEquals(VeteranOption.VETERAN, VeteranOption.fromString("Veteran"))
        assertEquals(VeteranOption.VETERAN, VeteranOption.fromString("I identify as a veteran"))
        assertEquals(VeteranOption.NOT_A_VETERAN, VeteranOption.fromString("Not a veteran"))
        assertEquals(VeteranOption.NOT_A_VETERAN, VeteranOption.fromString("I am not a protected veteran"))
        assertEquals(VeteranOption.PREFER_NOT_TO_SAY, VeteranOption.fromString("Prefer not to say"))
        assertEquals(VeteranOption.PREFER_NOT_TO_SAY, VeteranOption.fromString("I decline to self-identify for protected veteran status"))
        assertNull(VeteranOption.fromString("unknown"))
    }

    @Test
    fun startDateOption_fromString_parsesCorrectly() {
        assertEquals(StartDateOption.IMMEDIATELY, StartDateOption.fromString("Immediately"))
        assertEquals(StartDateOption.IMMEDIATELY, StartDateOption.fromString("immediate"))
        assertEquals(StartDateOption.TWO_WEEKS, StartDateOption.fromString("2 Weeks"))
        assertEquals(StartDateOption.TWO_WEEKS, StartDateOption.fromString("two weeks"))
        assertEquals(StartDateOption.ONE_MONTH, StartDateOption.fromString("1 Month"))
        assertEquals(StartDateOption.ONE_MONTH, StartDateOption.fromString("one month"))
        assertNull(StartDateOption.fromString("next year"))
    }

    @Test
    fun applicantAnswerPresets_containsExpectedPresets() {
        assertEquals(9, ApplicantAnswerPresets.PRESETS.size)

        val workAuth = ApplicantAnswerPresets.findPreset("work_authorization")
        assertNotNull(workAuth)
        assertTrue(workAuth!!.inputType is PresetInputType.SingleChoice)
        assertEquals(listOf("Yes", "No"), (workAuth.inputType as PresetInputType.SingleChoice).options)

        val sponsorship = ApplicantAnswerPresets.findPreset("sponsorship_needed")
        assertNotNull(sponsorship)
        assertTrue(sponsorship!!.inputType is PresetInputType.SingleChoice)

        val relocate = ApplicantAnswerPresets.findPreset("willing_to_relocate")
        assertNotNull(relocate)
        assertTrue(relocate!!.inputType is PresetInputType.SingleChoice)

        val gender = ApplicantAnswerPresets.findPreset("eeo_gender")
        assertNotNull(gender)
        assertTrue(gender!!.inputType is PresetInputType.SingleChoice)
        assertEquals(
            listOf("Male", "Female", "Non-binary", "Prefer not to say"),
            (gender.inputType as PresetInputType.SingleChoice).options
        )

        val veteran = ApplicantAnswerPresets.findPreset("eeo_veteran_status")
        assertNotNull(veteran)
        assertTrue(veteran!!.inputType is PresetInputType.SingleChoice)
        assertEquals(
            listOf("Veteran", "Not a veteran", "Prefer not to say"),
            (veteran.inputType as PresetInputType.SingleChoice).options
        )

        val disability = ApplicantAnswerPresets.findPreset("eeo_disability_status")
        assertNotNull(disability)
        assertTrue(disability!!.inputType is PresetInputType.SingleChoice)
        assertEquals(
            listOf("Yes", "No", "Prefer not to say"),
            (disability.inputType as PresetInputType.SingleChoice).options
        )

        val startDate = ApplicantAnswerPresets.findPreset("earliest_start_date")
        assertNotNull(startDate)
        assertTrue(startDate!!.inputType is PresetInputType.StartDate)
        assertEquals(
            listOf("Immediately", "2 Weeks", "1 Month"),
            (startDate.inputType as PresetInputType.StartDate).quickOptions
        )

        val salary = ApplicantAnswerPresets.findPreset("salary_expectation")
        assertNotNull(salary)
        assertTrue(salary!!.inputType is PresetInputType.FreeText)

        val race = ApplicantAnswerPresets.findPreset("eeo_race_ethnicity")
        assertNotNull(race)
        assertTrue(race!!.inputType is PresetInputType.FreeText)
    }

    @Test
    fun applicantAnswerPresets_findPreset_fuzzyMatches() {
        assertNotNull(ApplicantAnswerPresets.findPreset("Are you legally authorized to work in the United States?"))
        assertNotNull(ApplicantAnswerPresets.findPreset("Will you now or in the future require visa sponsorship?"))
        assertNotNull(ApplicantAnswerPresets.findPreset("Are you willing to relocate?"))
        assertNotNull(ApplicantAnswerPresets.findPreset("Gender"))
        assertNotNull(ApplicantAnswerPresets.findPreset("Veteran Status"))
        assertNotNull(ApplicantAnswerPresets.findPreset("Disability Status"))
        assertNotNull(ApplicantAnswerPresets.findPreset("Earliest Start Date"))
    }

    @Test
    fun applicantAnswerEntity_factoryAndHelpers_workCorrectly() {
        val auth = ApplicantAnswerEntity.workAuthorization(YesNoOption.YES)
        assertEquals(ApplicantAnswerEntity.PATTERN_WORK_AUTHORIZATION, auth.questionPattern)
        assertEquals("Yes", auth.answer)
        assertEquals(YesNoOption.YES, auth.yesNoOption)

        val gen = ApplicantAnswerEntity.gender(GenderOption.NON_BINARY)
        assertEquals(ApplicantAnswerEntity.PATTERN_EEO_GENDER, gen.questionPattern)
        assertEquals("Non-binary", gen.answer)
        assertEquals(GenderOption.NON_BINARY, gen.genderOption)

        val vet = ApplicantAnswerEntity.veteran(VeteranOption.VETERAN)
        assertEquals(ApplicantAnswerEntity.PATTERN_EEO_VETERAN, vet.questionPattern)
        assertEquals("Veteran", vet.answer)
        assertEquals(VeteranOption.VETERAN, vet.veteranOption)

        val dis = ApplicantAnswerEntity.disability(EEOOption.PREFER_NOT_TO_SAY)
        assertEquals(ApplicantAnswerEntity.PATTERN_EEO_DISABILITY, dis.questionPattern)
        assertEquals("Prefer not to say", dis.answer)
        assertEquals(EEOOption.PREFER_NOT_TO_SAY, dis.eeoOption)

        val rel = ApplicantAnswerEntity.relocate(YesNoOption.NO)
        assertEquals(ApplicantAnswerEntity.PATTERN_WILLING_TO_RELOCATE, rel.questionPattern)
        assertEquals("No", rel.answer)
        assertEquals(YesNoOption.NO, rel.yesNoOption)

        val start = ApplicantAnswerEntity.startDate(StartDateOption.TWO_WEEKS)
        assertEquals(ApplicantAnswerEntity.PATTERN_EARLIEST_START_DATE, start.questionPattern)
        assertEquals("2 Weeks", start.answer)
        assertEquals(StartDateOption.TWO_WEEKS, start.startDateOption)
    }
}
