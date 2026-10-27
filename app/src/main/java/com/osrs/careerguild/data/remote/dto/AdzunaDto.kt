package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdzunaResponseDto(
    @SerialName("results") val results: List<AdzunaJobDto> = emptyList(),
    @SerialName("count") val count: Int? = null,
)

@Serializable
data class AdzunaJobDto(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
    @SerialName("company") val company: AdzunaCompanyDto? = null,
    @SerialName("location") val location: AdzunaLocationDto? = null,
    @SerialName("created") val created: String? = null,
    @SerialName("salary_min") val salaryMin: Double? = null,
    @SerialName("salary_max") val salaryMax: Double? = null,
)

@Serializable
data class AdzunaCompanyDto(
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class AdzunaLocationDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("area") val area: List<String> = emptyList(),
)
