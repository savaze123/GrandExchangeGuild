package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GreenhouseResponseDto(
    @SerialName("jobs") val jobs: List<GreenhouseJobDto> = emptyList()
)

@Serializable
data class GreenhouseJobDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("absolute_url") val absoluteUrl: String? = null,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("location") val location: GreenhouseLocationDto? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("departments") val departments: List<GreenhouseDepartmentDto> = emptyList(),
    @SerialName("metadata") val metadata: List<GreenhouseMetadataDto> = emptyList(),
)

@Serializable
data class GreenhouseLocationDto(
    @SerialName("name") val name: String? = null,
)

@Serializable
data class GreenhouseDepartmentDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class GreenhouseMetadataDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("value") val value: String? = null,
)
