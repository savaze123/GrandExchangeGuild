package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.core.network.PoliteHttpClient
import com.osrs.careerguild.data.remote.dto.ArbeitnowResponseDto
import com.osrs.careerguild.data.remote.scraper.GeoFilter
import com.osrs.careerguild.domain.model.JobListing
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

/**
 * Ingests listings from Arbeitnow Direct API:
 * https://www.arbeitnow.com/api/job-board-api
 * Filters for remote or US-based software & AI roles.
 */
class ArbeitnowSource(
    private val client: PoliteHttpClient,
    private val geoFilter: GeoFilter = GeoFilter(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) {
    suspend fun fetchJobs(
        activeSkillNames: List<String> = emptyList(),
        limit: Int = 50,
    ): List<JobListing> = runCatching {
        val url = "https://www.arbeitnow.com/api/job-board-api"
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/json")
        }
        val bodyText = response.bodyAsText()
        val dto = json.decodeFromString<ArbeitnowResponseDto>(bodyText)

        val techKeywords = listOf(
            "engineer", "developer", "software", "devops", "architect",
            "frontend", "backend", "fullstack", "data", "android", "ios",
            "cloud", "security", "ai", "machine learning", "python", "golang",
            "kotlin", "java", "typescript", "systems"
        )
        val activeSkillLower = activeSkillNames.map { it.lowercase() }

        dto.data.asSequence()
            .mapNotNull { job ->
                val comp = job.companyName?.trim() ?: return@mapNotNull null
                val title = job.title?.trim() ?: return@mapNotNull null
                val rawUrl = job.url?.trim() ?: return@mapNotNull null
                if (comp.isBlank() || title.isBlank() || rawUrl.isBlank()) return@mapNotNull null

                val titleLower = title.lowercase()
                val tagsLower = (job.tags ?: emptyList()).map { it.lowercase() }
                val isTech = techKeywords.any { titleLower.contains(it) || tagsLower.any { t -> t.contains(it) } } ||
                    activeSkillLower.any { titleLower.contains(it) }
                if (!isTech) return@mapNotNull null

                val loc = job.location?.trim()?.ifBlank { null }
                    ?: (if (job.remote == true) "Remote" else "Worldwide")

                // Must be remote or pass US geo filter
                val isRemote = job.remote == true || loc.contains("Remote", true)
                val locList = if (isRemote) listOf(loc, "Remote") else listOf(loc)
                if (!geoFilter.passes(locList)) return@mapNotNull null

                val fullText = "$title $comp ${tagsLower.joinToString(" ")}"
                val matched = activeSkillNames.filterIndexed { index, _ ->
                    fullText.contains(activeSkillLower[index])
                }

                val baseScore = 75
                val matchBonus = matched.size * 5
                val finalScore = minOf(99, maxOf(50, baseScore + matchBonus))

                val desc = job.description?.take(800)?.replace(Regex("<.*?>"), " ")?.trim()?.ifBlank { null }
                    ?: "$comp is hiring a $title ($loc)."

                val workMode = if (isRemote) "Remote" else if (loc.contains("Hybrid", true)) "Hybrid" else "Local"

                JobListing(
                    id = "arbeitnow_${job.slug ?: (comp + title).hashCode()}".replace(Regex("[^a-z0-9]"), "_").take(64),
                    title = title,
                    company = comp,
                    atsUrl = rawUrl,
                    source = "Arbeitnow (Direct)",
                    score = finalScore,
                    matchedSkills = if (matched.isNotEmpty()) matched else (activeSkillNames.take(3) + listOf("Software Engineering", "Full-Stack")).distinct(),
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
        android.util.Log.d("ArbeitnowSource", "Arbeitnow fetch failed: ${it.message}")
        emptyList()
    }
}
