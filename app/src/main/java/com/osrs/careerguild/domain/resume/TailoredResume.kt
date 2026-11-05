package com.osrs.careerguild.domain.resume

import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.CategorizedSkill
import com.osrs.careerguild.core.resume.ResumeCertification
import com.osrs.careerguild.core.resume.ResumeContact
import com.osrs.careerguild.core.resume.ResumeEducation
import com.osrs.careerguild.core.resume.ResumeExperience
import com.osrs.careerguild.core.resume.ResumeProject
import com.osrs.careerguild.core.resume.SkillCategorizer
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
    val categorizedSkills: List<CategorizedSkill> = emptyList(),
    val experience: List<ResumeExperience> = emptyList(),
    val projects: List<ResumeProject> = emptyList(),
    val education: List<ResumeEducation> = emptyList(),
    val certifications: List<ResumeCertification> = emptyList(),
    val isTailored: Boolean = true,
    val fallbackReason: String? = null,
) {
    fun getCategorizedSkills(jdKeywords: List<String> = emptyList()): Map<String, List<String>> {
        if (categorizedSkills.isNotEmpty()) {
            val grouped = categorizedSkills.groupBy { it.category }
                .mapValues { (_, list) -> list.map { it.name } }
            val ordered = linkedMapOf<String, List<String>>()
            for (cat in SkillCategorizer.ORDERED_CATEGORIES) {
                grouped[cat]?.let { if (it.isNotEmpty()) ordered[cat] = it }
            }
            for ((cat, list) in grouped) {
                if (cat !in ordered && list.isNotEmpty()) ordered[cat] = list
            }
            return ordered
        }
        return SkillCategorizer.categorize(skills, jdKeywords)
    }
    companion object {
        private val trailingMonthRegex = Regex(
            """^(.*?)(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)$"""
        )

        fun sanitizeExperience(exp: ResumeExperience): ResumeExperience {
            var cleanEmployer = exp.employer.trim()
            var cleanTitle = exp.title.trim()
            var cleanStart = exp.startDate?.trim()
            trailingMonthRegex.find(cleanEmployer)?.let { m ->
                val prefix = m.groupValues[1].trim()
                val month = m.groupValues[2].trim()
                if (prefix.isNotEmpty()) {
                    cleanEmployer = prefix
                    if (!cleanStart.isNullOrBlank() && !cleanStart!!.contains(month, ignoreCase = true)) {
                        cleanStart = "$month $cleanStart"
                    }
                }
            }
            trailingMonthRegex.find(cleanTitle)?.let { m ->
                val prefix = m.groupValues[1].trim()
                val month = m.groupValues[2].trim()
                if (prefix.isNotEmpty()) {
                    cleanTitle = prefix
                    if (!cleanStart.isNullOrBlank() && !cleanStart!!.contains(month, ignoreCase = true)) {
                        cleanStart = "$month $cleanStart"
                    }
                }
            }
            return exp.copy(
                employer = cleanEmployer,
                title = cleanTitle,
                startDate = cleanStart,
            )
        }

        fun empty(): TailoredResume = TailoredResume(isTailored = false)

        fun fromBaseResume(base: BaseResume, reason: String? = null): TailoredResume {
            val activeBase = base.activeOnly()
            return TailoredResume(
                contact = activeBase.contact,
                summary = activeBase.summary ?: "",
                skills = activeBase.skills,
                categorizedSkills = activeBase.categorizedSkills,
                experience = activeBase.experience.filter { it.isActive }.map { sanitizeExperience(it) },
                projects = activeBase.projects.filter { it.isActive },
                education = activeBase.education.filter { it.isActive },
                certifications = activeBase.certifications.filter { it.isActive },
                isTailored = false,
                fallbackReason = reason,
            )
        }
    }
}
