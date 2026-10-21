package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AshbyResponseDto(
    @SerialName("jobs") val jobs: List<AshbyJobDto> = emptyList()
)

@Serializable
data class AshbyJobDto(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("department") val department: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("isRemote") val isRemote: Boolean? = null,
    @SerialName("jobUrl") val jobUrl: String? = null,
    @SerialName("applyUrl") val applyUrl: String? = null,
)
