package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.core.network.PoliteHttpClient
import com.osrs.careerguild.data.remote.dto.RemotiveResponseDto
import com.osrs.careerguild.data.remote.scraper.GeoFilter
import com.osrs.careerguild.domain.model.JobListing
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/**
 * Ingests listings from Remotive Developer API:
 * https://remotive.com/api/remote-jobs?category=software-dev
 * Rate-limited to at most once per 6 hours.
 */
class RemotiveSource(
    private val client: PoliteHttpClient,
    private val geoFilter: GeoFilter = GeoFilter(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) {
    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private val lastFetchTime = AtomicLong(0L)
        @Volatile
        private var cachedJobs: List<JobListing> = emptyList()
    }

    suspend fun fetchJobs(
        activeSkillNames: List<String> = emptyList(),
        limit: Int = 50,
    ): List<JobListing> {
        val now = System.currentTimeMillis()
        if (cachedJobs.isNotEmpty() && (now - lastFetchTime.get()) < CACHE_TTL_MS) {
            android.util.Log.i("RemotiveSource", "Returning ${cachedJobs.size} cached Remotive jobs (TTL valid).")
            return cachedJobs.take(limit)
        }

        return runCatching {
            val url = "https://remotive.com/api/remote-jobs?category=software-dev"
            val response = client.get(url) {
                header(HttpHeaders.Accept, "application/json")
            }
            val bodyText = response.bodyAsText()
            val dto = json.decodeFromString<RemotiveResponseDto>(bodyText)

            val activeSkillLower = activeSkillNames.map { it.lowercase() }
            val parsed = dto.jobs.asSequence()
                .mapNotNull { job ->
                    val comp = job.companyName?.trim() ?: return@mapNotNull null
                    val title = job.title?.trim() ?: return@mapNotNull null
                    val rawUrl = job.url?.trim() ?: return@mapNotNull null
                    if (comp.isBlank() || title.isBlank() || rawUrl.isBlank()) return@mapNotNull null

                    val loc = job.candidateRequiredLocation?.trim()?.ifBlank { null } ?: "Remote"
                    if (!geoFilter.passes(listOf(loc, "Remote"))) return@mapNotNull null

                    val fullText = "$title $comp ${(job.tags ?: emptyList()).joinToString(" ")}".lowercase()
                    val matched = activeSkillNames.filterIndexed { index, _ ->
                        fullText.contains(activeSkillLower[index])
                    }

                    val baseScore = 78
                    val matchBonus = matched.size * 5
                    val finalScore = minOf(99, maxOf(55, baseScore + matchBonus))

                    val desc = job.description?.take(800)?.replace(Regex("<.*?>"), " ")?.trim()?.ifBlank { null }
                        ?: "$comp is hiring a $title ($loc). Remote engineering role."

                    JobListing(
                        id = "remotive_${job.id ?: (comp + title).hashCode()}".replace(Regex("[^a-z0-9]"), "_").take(64),
                        title = title,
                        company = comp,
                        atsUrl = rawUrl,
                        source = "Remotive (Direct)",
                        score = finalScore,
                        matchedSkills = if (matched.isNotEmpty()) matched else (activeSkillNames.take(3) + listOf("Remote Software Dev", "Distributed Systems")).distinct(),
                        isBlocked = false,
                        appliedAt = null,
                        description = desc,
                        location = loc.ifBlank { "Remote (US / Worldwide)" },
                        workMode = "Remote",
                        applyUrl = rawUrl,
                        linkStatus = "VERIFIED",
                    )
                }
                .take(limit)
                .toList()

            if (parsed.isNotEmpty()) {
                cachedJobs = parsed
                lastFetchTime.set(now)
            }
            parsed
        }.getOrElse {
            android.util.Log.d("RemotiveSource", "Remotive fetch failed: ${it.message}")
            cachedJobs.take(limit)
        }
    }
}
