package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArbeitnowResponseDto(
    @SerialName("data") val data: List<ArbeitnowJobDto> = emptyList()
)

@Serializable
data class ArbeitnowJobDto(
    @SerialName("slug") val slug: String? = null,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("remote") val remote: Boolean? = null,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("description") val description: String? = null,
)
