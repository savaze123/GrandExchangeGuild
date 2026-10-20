package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for RapidAPI LinkedIn / JSearch / Fantastic.jobs feed responses.
 */
@Serializable
data class RapidApiLinkedInResponseDto(
    @SerialName("status") val status: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("data") val data: List<RapidApiLinkedInJobDto> = emptyList(),
)

@Serializable
data class RapidApiLinkedInJobDto(
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("employer_name") val employerName: String? = null,
    @SerialName("employer_logo") val employerLogo: String? = null,
    @SerialName("job_apply_link") val jobApplyLink: String? = null,
    @SerialName("job_description") val jobDescription: String? = null,
    @SerialName("job_city") val jobCity: String? = null,
    @SerialName("job_state") val jobState: String? = null,
    @SerialName("job_country") val jobCountry: String? = null,
    @SerialName("job_is_remote") val jobIsRemote: Boolean? = false,
    @SerialName("job_posted_at_timestamp") val jobPostedAtTimestamp: Long? = null,
)

/**
 * Standardized parsed model for direct LinkedIn job listings.
 */
data class LinkedInJobItem(
    val id: String,
    val title: String,
    val company: String,
    val url: String,
    val location: String = "United States",
    val description: String = "",
)
