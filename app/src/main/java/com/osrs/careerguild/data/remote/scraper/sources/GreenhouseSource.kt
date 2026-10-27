package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.core.network.PoliteHttpClient
import com.osrs.careerguild.data.remote.dto.GreenhouseResponseDto
import com.osrs.careerguild.data.remote.scraper.GeoFilter
import com.osrs.careerguild.domain.model.JobListing
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

/**
 * Ingests direct requisitions from public Greenhouse ATS endpoints:
 * https://boards-api.greenhouse.io/v1/boards/{company}/jobs
 */
class GreenhouseSource(
    private val client: PoliteHttpClient,
    private val geoFilter: GeoFilter = GeoFilter(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) {
    suspend fun fetchJobs(
        companySlug: String,
        companyName: String,
        activeSkillNames: List<String> = emptyList(),
        limit: Int = 50,
    ): List<JobListing> = runCatching {
        val url = "https://boards-api.greenhouse.io/v1/boards/$companySlug/jobs"
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/json")
        }
        val bodyText = response.bodyAsText()
        val dto = json.decodeFromString<GreenhouseResponseDto>(bodyText)

        val techKeywords = listOf(
            "engineer", "developer", "software", "systems", "platform", "backend",
            "infrastructure", "devops", "sre", "security", "android", "ios",
            "kernel", "cloud", "compiler", "runtime", "data", "ai", "machine learning"
        )
        val activeSkillLower = activeSkillNames.map { it.lowercase() }

        dto.jobs.asSequence()
            .mapNotNull { job ->
                val title = job.title?.trim() ?: return@mapNotNull null
                val rawUrl = job.absoluteUrl?.trim() ?: return@mapNotNull null
                if (title.isBlank() || rawUrl.isBlank()) return@mapNotNull null

                val titleLower = title.lowercase()
                val isTech = techKeywords.any { titleLower.contains(it) } ||
                    activeSkillLower.any { titleLower.contains(it) }
                if (!isTech) return@mapNotNull null

                val loc = job.location?.name?.trim()?.ifBlank { null } ?: "Hybrid / Remote"
                if (!geoFilter.passes(listOf(loc))) return@mapNotNull null

                val fullText = "$title $companyName $loc".lowercase()
                val matched = activeSkillNames.filterIndexed { index, _ ->
                    fullText.contains(activeSkillLower[index])
                }

                val baseScore = 82
                val matchBonus = matched.size * 4
                val finalScore = minOf(99, maxOf(60, baseScore + matchBonus))

                val workMode = when {
                    loc.contains("Remote", ignoreCase = true) || title.contains("Remote", ignoreCase = true) -> "Remote"
                    loc.contains("Hybrid", ignoreCase = true) || title.contains("Hybrid", ignoreCase = true) -> "Hybrid"
                    else -> "Local"
                }

                val depts = job.departments.mapNotNull { it.name }.joinToString(", ")
                val desc = buildString {
                    append("$companyName is hiring a $title in $loc.")
                    if (depts.isNotBlank()) append(" Department: $depts.")
                    if (matched.isNotEmpty()) append(" Matched competencies: ${matched.joinToString(", ")}.")
                }

                JobListing(
                    id = "gh_${companySlug}_${job.id ?: (companyName + title).hashCode()}".replace(Regex("[^a-z0-9]"), "_").take(64),
                    title = title,
                    company = companyName,
                    atsUrl = rawUrl,
                    source = "$companyName (Greenhouse)",
                    score = finalScore,
                    matchedSkills = if (matched.isNotEmpty()) matched else (activeSkillNames.take(3) + listOf("Software Engineering")).distinct(),
                    isBlocked = false,
                    appliedAt = null,
                    description = desc,
                    location = loc,
                    workMode = workMode,
                    applyUrl = rawUrl,
                    linkStatus = "VERIFIED",
                )
            }
            .take(limit)
            .toList()
    }.getOrElse {
        android.util.Log.d("GreenhouseSource", "Fetch error for $companyName ($companySlug): ${it.message}")
        emptyList()
    }
}
