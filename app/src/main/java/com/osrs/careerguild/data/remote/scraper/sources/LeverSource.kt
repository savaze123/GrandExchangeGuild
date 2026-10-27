package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.core.network.PoliteHttpClient
import com.osrs.careerguild.data.remote.dto.LeverJobDto
import com.osrs.careerguild.data.remote.scraper.GeoFilter
import com.osrs.careerguild.domain.model.JobListing
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

/**
 * Ingests direct requisitions from public Lever ATS endpoints:
 * https://api.lever.co/v0/postings/{company}?mode=json
 */
class LeverSource(
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
        val url = "https://api.lever.co/v0/postings/$companySlug?mode=json"
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/json")
        }
        val bodyText = response.bodyAsText()
        val dtoList = json.decodeFromString<List<LeverJobDto>>(bodyText)

        val techKeywords = listOf(
            "engineer", "developer", "software", "systems", "platform", "backend",
            "infrastructure", "devops", "sre", "security", "android", "ios",
            "kernel", "cloud", "compiler", "runtime", "data", "ai", "machine learning"
        )
        val activeSkillLower = activeSkillNames.map { it.lowercase() }

        dtoList.asSequence()
            .mapNotNull { job ->
                val title = job.text?.trim() ?: return@mapNotNull null
                val directUrl = job.applyUrl?.trim() ?: job.hostedUrl?.trim() ?: return@mapNotNull null
                if (title.isBlank() || directUrl.isBlank()) return@mapNotNull null

                val titleLower = title.lowercase()
                val isTech = techKeywords.any { titleLower.contains(it) } ||
                    activeSkillLower.any { titleLower.contains(it) }
                if (!isTech) return@mapNotNull null

                val loc = job.categories?.location?.trim()?.ifBlank { null }
                    ?: (if (job.workplaceType.equals("remote", true)) "Remote" else "Hybrid / Local")
                if (!geoFilter.passes(listOf(loc))) return@mapNotNull null

                val fullText = "$title $companyName $loc".lowercase()
                val matched = activeSkillNames.filterIndexed { index, _ ->
                    fullText.contains(activeSkillLower[index])
                }

                val baseScore = 82
                val matchBonus = matched.size * 4
                val finalScore = minOf(99, maxOf(60, baseScore + matchBonus))

                val workMode = when {
                    job.workplaceType.equals("remote", true) || loc.contains("Remote", true) || title.contains("Remote", true) -> "Remote"
                    job.workplaceType.equals("hybrid", true) || loc.contains("Hybrid", true) || title.contains("Hybrid", true) -> "Hybrid"
                    else -> "Local"
                }

                val team = job.categories?.team ?: job.categories?.department ?: ""
                val desc = job.description?.take(600)?.replace(Regex("<.*?>"), " ")?.trim()?.ifBlank { null }
                    ?: buildString {
                        append("$companyName is hiring a $title in $loc.")
                        if (team.isNotBlank()) append(" Team: $team.")
                        if (matched.isNotEmpty()) append(" Skills matched: ${matched.joinToString(", ")}.")
                    }

                JobListing(
                    id = "lever_${companySlug}_${job.id ?: (companyName + title).hashCode()}".replace(Regex("[^a-z0-9]"), "_").take(64),
                    title = title,
                    company = companyName,
                    atsUrl = directUrl,
                    source = "$companyName (Lever)",
                    score = finalScore,
                    matchedSkills = if (matched.isNotEmpty()) matched else (activeSkillNames.take(3) + listOf("Software Engineering")).distinct(),
                    isBlocked = false,
                    appliedAt = null,
                    description = desc,
                    location = loc,
                    workMode = workMode,
                    applyUrl = directUrl,
                    linkStatus = "VERIFIED",
                )
            }
            .take(limit)
            .toList()
    }.getOrElse {
        android.util.Log.d("LeverSource", "Fetch error for $companyName ($companySlug): ${it.message}")
        emptyList()
    }
}
