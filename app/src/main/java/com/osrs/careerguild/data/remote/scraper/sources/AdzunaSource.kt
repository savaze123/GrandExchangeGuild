package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.BuildConfig
import com.osrs.careerguild.core.network.PoliteHttpClient
import com.osrs.careerguild.data.remote.dto.AdzunaResponseDto
import com.osrs.careerguild.data.remote.scraper.GeoFilter
import com.osrs.careerguild.domain.model.JobListing
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Ingests listings from Adzuna Job Search API.
 * Attributed as "Jobs by Adzuna" per terms of service.
 */
class AdzunaSource(
    private val client: PoliteHttpClient,
    private val geoFilter: GeoFilter = GeoFilter(),
    private val appId: String = BuildConfig.ADZUNA_APP_ID,
    private val appKey: String = BuildConfig.ADZUNA_APP_KEY,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) {
    val isConfigured: Boolean
        get() = appId.isNotBlank() && appKey.isNotBlank()

    suspend fun fetchJobs(
        titles: List<String> = listOf("Software Engineer", "AI Engineer", "Data Engineer", "Android Developer"),
        pagesPerTitle: Int = 3,
        activeSkillNames: List<String> = emptyList(),
    ): List<JobListing> {
        if (!isConfigured) {
            android.util.Log.i("AdzunaSource", "Adzuna credentials not configured. Skipping Adzuna search.")
            return emptyList()
        }

        val results = mutableListOf<JobListing>()
        val activeSkillLower = activeSkillNames.map { it.lowercase() }

        for (titleQuery in titles) {
            for (page in 1..pagesPerTitle) {
                try {
                    val encodedTitle = URLEncoder.encode(titleQuery, "UTF-8")
                    val url = "https://api.adzuna.com/v1/api/jobs/us/search/$page?app_id=$appId&app_key=$appKey&what=$encodedTitle&content-type=application/json"
                    val response = client.get(url) {
                        header(HttpHeaders.Accept, "application/json")
                    }
                    val bodyText = response.bodyAsText()
                    val dto = json.decodeFromString<AdzunaResponseDto>(bodyText)

                    for (job in dto.results) {
                        val title = job.title?.trim() ?: continue
                        val rawUrl = job.redirectUrl?.trim() ?: continue
                        val company = job.company?.displayName?.trim() ?: "Tech Employer"
                        if (title.isBlank() || rawUrl.isBlank()) continue

                        val loc = job.location?.displayName?.trim()?.ifBlank { null } ?: "United States"
                        if (!geoFilter.passes(listOf(loc))) continue

                        val fullText = "$title $company $loc".lowercase()
                        val matched = activeSkillNames.filterIndexed { index, _ ->
                            fullText.contains(activeSkillLower[index])
                        }

                        val baseScore = 80
                        val matchBonus = matched.size * 3
                        val finalScore = minOf(99, maxOf(50, baseScore + matchBonus))

                        val workMode = when {
                            loc.contains("Remote", true) || title.contains("Remote", true) -> "Remote"
                            loc.contains("Hybrid", true) || title.contains("Hybrid", true) -> "Hybrid"
                            else -> "Local"
                        }

                        results.add(
                            JobListing(
                                id = "adzuna_${job.id ?: (company + title).hashCode()}".replace(Regex("[^a-z0-9]"), "_").take(64),
                                title = title,
                                company = company,
                                atsUrl = rawUrl,
                                source = "Jobs by Adzuna",
                                score = finalScore,
                                matchedSkills = if (matched.isNotEmpty()) matched else (activeSkillNames.take(3) + listOf("Software Engineering")).distinct(),
                                isBlocked = false,
                                appliedAt = null,
                                description = job.description?.take(800) ?: "$company is hiring $title in $loc.",
                                location = loc,
                                workMode = workMode,
                                applyUrl = rawUrl,
                                linkStatus = "TRUSTED_UNVERIFIED",
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.d("AdzunaSource", "Error querying Adzuna for '$titleQuery' p$page: ${e.message}")
                    break
                }
            }
        }
        return results
    }
}
