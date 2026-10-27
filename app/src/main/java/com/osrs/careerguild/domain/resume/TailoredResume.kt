package com.osrs.careerguild.domain.resume

import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeCertification
import com.osrs.careerguild.core.resume.ResumeContact
import com.osrs.careerguild.core.resume.ResumeEducation
import com.osrs.careerguild.core.resume.ResumeExperience
import com.osrs.careerguild.core.resume.ResumeProject
import kotlinx.serialization.Serializable

/**
 * Tailored resume model derived strictly from [BaseResume] and verified public GitHub data.
 * Governed by [ResumeVerifier] to prevent any hallucination or fabrication of numbers, metrics, or credentials.
 */
@Serializable
data class TailoredResume(
    val contact: ResumeContact = ResumeContact(),
    val summary: String = "",
    val skills: List<String> = emptyList(),
    val experience: List<ResumeExperience> = emptyList(),
    val projects: List<ResumeProject> = emptyList(),
    val education: List<ResumeEducation> = emptyList(),
    val certifications: List<ResumeCertification> = emptyList(),
    val isTailored: Boolean = true,
    val fallbackReason: String? = null,
) {
    companion object {
        fun empty(): TailoredResume = TailoredResume(isTailored = false)

        fun fromBaseResume(base: BaseResume, reason: String? = null): TailoredResume {
            return TailoredResume(
                contact = base.contact,
                summary = base.summary ?: "",
                skills = base.skills,
                experience = base.experience,
                projects = base.projects,
                education = base.education,
                certifications = base.certifications,
                isTailored = false,
                fallbackReason = reason,
            )
        }
    }
}
