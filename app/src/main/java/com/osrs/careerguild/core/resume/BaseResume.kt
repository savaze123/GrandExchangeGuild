package com.osrs.careerguild.core.resume

import kotlinx.serialization.Serializable

/**
 * Single source of truth candidate resume schema.
 * Built strictly from the user's uploaded base resume plus verified public GitHub data.
 */
@Serializable
data class BaseResume(
    val contact: ResumeContact = ResumeContact(),
    val summary: String? = null,
    val experience: List<ResumeExperience> = emptyList(),
    val education: List<ResumeEducation> = emptyList(),
    val certifications: List<ResumeCertification> = emptyList(),
    val projects: List<ResumeProject> = emptyList(),
    val skills: List<String> = emptyList(),
    val categorizedSkills: List<CategorizedSkill> = emptyList(),
) {
    /**
     * Returns a copy of this [BaseResume] containing only active (`isActive == true`)
     * Experience, Projects, Education, and Certifications items.
     */
    fun activeOnly(): BaseResume = copy(
        experience = experience.filter { it.isActive },
        education = education.filter { it.isActive },
        certifications = certifications.filter { it.isActive },
        projects = projects.filter { it.isActive },
    )

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
}

@Serializable
data class CategorizedSkill(
    val name: String,
    val category: String,
)

@Serializable
data class ResumeContact(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val github: String? = null,
    val linkedin: String? = null,
    val website: String? = null,
)

@Serializable
data class ResumeBulletMetric(
    val bulletText: String,
    val userMetric: String? = null,
    val userProvided: Boolean = true,
)

@Serializable
data class ResumeExperience(
    val employer: String = "",
    val title: String = "",
    val location: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val isCurrent: Boolean = false,
    val isConfirmedCurrent: Boolean = false,
    val bullets: List<String> = emptyList(),
    val isActive: Boolean = true,
    val bulletMetrics: List<ResumeBulletMetric> = emptyList(),
    val userMetrics: Map<String, String> = emptyMap(),
)

@Serializable
data class ResumeEducation(
    val school: String = "",
    val degree: String = "",
    val fieldOfStudy: String? = null,
    val graduationDate: String? = null,
    val gpa: String? = null,
    val details: List<String> = emptyList(),
    val isActive: Boolean = true,
)

@Serializable
data class ResumeCertification(
    val name: String = "",
    val issuer: String? = null,
    val issueDate: String? = null,
    val credentialId: String? = null,
    val details: List<String> = emptyList(),
    val isActive: Boolean = true,
)

@Serializable
data class ResumeProject(
    val name: String = "",
    val role: String? = null,
    val technologies: List<String> = emptyList(),
    val url: String? = null,
    val bullets: List<String> = emptyList(),
    val isActive: Boolean = true,
    val bulletMetrics: List<ResumeBulletMetric> = emptyList(),
    val userMetrics: Map<String, String> = emptyMap(),
)
