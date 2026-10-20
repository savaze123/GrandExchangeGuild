package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemotiveResponseDto(
    @SerialName("jobs") val jobs: List<RemotiveJobDto> = emptyList()
)

@Serializable
data class RemotiveJobDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("candidate_required_location") val candidateRequiredLocation: String? = null,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("description") val description: String? = null,
)
