package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.core.network.PoliteHttpClient
import com.osrs.careerguild.data.remote.dto.AshbyResponseDto
import com.osrs.careerguild.data.remote.scraper.GeoFilter
import com.osrs.careerguild.domain.model.JobListing
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

/**
 * Ingests direct requisitions from public Ashby ATS endpoints:
 * https://api.ashbyhq.com/posting-api/job-board/{company}
 */
class AshbySource(
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
        val url = "https://api.ashbyhq.com/posting-api/job-board/$companySlug"
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/json")
        }
        val bodyText = response.bodyAsText()
        val dto = json.decodeFromString<AshbyResponseDto>(bodyText)

        val techKeywords = listOf(
            "engineer", "developer", "software", "systems", "platform", "backend",
            "infrastructure", "devops", "sre", "security", "android", "ios",
            "kernel", "cloud", "compiler", "runtime", "data", "ai", "machine learning"
        )
        val activeSkillLower = activeSkillNames.map { it.lowercase() }

        dto.jobs.asSequence()
            .mapNotNull { job ->
                val title = job.title?.trim() ?: return@mapNotNull null
                val directUrl = job.applyUrl?.trim() ?: job.jobUrl?.trim() ?: return@mapNotNull null
                if (title.isBlank() || directUrl.isBlank()) return@mapNotNull null

                val titleLower = title.lowercase()
                val isTech = techKeywords.any { titleLower.contains(it) } ||
                    activeSkillLower.any { titleLower.contains(it) }
                if (!isTech) return@mapNotNull null

                val loc = job.location?.trim()?.ifBlank { null }
                    ?: (if (job.isRemote == true) "Remote" else "Hybrid / Local")
                if (!geoFilter.passes(listOf(loc))) return@mapNotNull null

                val fullText = "$title $companyName $loc".lowercase()
                val matched = activeSkillNames.filterIndexed { index, _ ->
                    fullText.contains(activeSkillLower[index])
                }

                val baseScore = 85
                val matchBonus = matched.size * 4
                val finalScore = minOf(99, maxOf(65, baseScore + matchBonus))

                val workMode = when {
                    job.isRemote == true || loc.contains("Remote", true) || title.contains("Remote", true) -> "Remote"
                    loc.contains("Hybrid", true) || title.contains("Hybrid", true) -> "Hybrid"
                    else -> "Local"
                }

                val dept = job.department ?: ""
                val desc = buildString {
                    append("$companyName is hiring a $title in $loc.")
                    if (dept.isNotBlank()) append(" Department: $dept.")
                    if (matched.isNotEmpty()) append(" Skills matched: ${matched.joinToString(", ")}.")
                }

                JobListing(
                    id = "ashby_${companySlug}_${job.id ?: (companyName + title).hashCode()}".replace(Regex("[^a-z0-9]"), "_").take(64),
                    title = title,
                    company = companyName,
                    atsUrl = directUrl,
                    source = "$companyName (Ashby)",
                    score = finalScore,
                    matchedSkills = if (matched.isNotEmpty()) matched else (activeSkillNames.take(3) + listOf("Software Engineering", "AI Systems")).distinct(),
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
        android.util.Log.d("AshbySource", "Fetch error for $companyName ($companySlug): ${it.message}")
        emptyList()
    }
}
