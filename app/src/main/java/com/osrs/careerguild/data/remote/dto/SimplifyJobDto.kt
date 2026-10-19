package com.osrs.careerguild.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for a single job listing from the SimplifyJobs / PittCSC GitHub JSON feeds.
 *
 * Feed URLs consumed by [TECH_RADAR_SCRAPER]:
 *  - New Grad: https://raw.githubusercontent.com/SimplifyJobs/New-Grad-Positions/dev/.github/scripts/listings.json
 *  - Internship: https://raw.githubusercontent.com/SimplifyJobs/Summer2026-Internships/dev/.github/scripts/listings.json
 *
 * Fields are nullable because the feed schema evolves and partial records are common.
 */
@Serializable
data class SimplifyJobDto(
    @SerialName("company_name")  val companyName  : String?        = null,
    @SerialName("title")          val title         : String?        = null,
    @SerialName("url")            val url           : String?        = null,
    @SerialName("date_posted")    val datePosted    : Long?          = null,
    @SerialName("active")         val active        : Boolean?       = null,
    @SerialName("is_visible")     val isVisible     : Boolean?       = null,
    @SerialName("locations")      val locations     : List<String>?  = null,
    @SerialName("sponsorship")    val sponsorship   : String?        = null,
    @SerialName("season")         val season        : String?        = null,
)
