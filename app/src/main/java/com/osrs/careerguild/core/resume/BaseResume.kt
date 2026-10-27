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
data class ResumeExperience(
    val employer: String = "",
    val title: String = "",
    val location: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val isCurrent: Boolean = false,
    val bullets: List<String> = emptyList(),
)

@Serializable
data class ResumeEducation(
    val school: String = "",
    val degree: String = "",
    val fieldOfStudy: String? = null,
    val graduationDate: String? = null,
    val gpa: String? = null,
    val details: List<String> = emptyList(),
)

@Serializable
data class ResumeCertification(
    val name: String = "",
    val issuer: String? = null,
    val issueDate: String? = null,
    val credentialId: String? = null,
    val details: List<String> = emptyList(),
)

@Serializable
data class ResumeProject(
    val name: String = "",
    val role: String? = null,
    val technologies: List<String> = emptyList(),
    val url: String? = null,
    val bullets: List<String> = emptyList(),
)
