package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LeverJobDto(
    @SerialName("id") val id: String? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("applyUrl") val applyUrl: String? = null,
    @SerialName("hostedUrl") val hostedUrl: String? = null,
    @SerialName("categories") val categories: LeverCategoriesDto? = null,
    @SerialName("workplaceType") val workplaceType: String? = null,
)

@Serializable
data class LeverCategoriesDto(
    @SerialName("location") val location: String? = null,
    @SerialName("team") val team: String? = null,
    @SerialName("department") val department: String? = null,
    @SerialName("commitment") val commitment: String? = null,
)
