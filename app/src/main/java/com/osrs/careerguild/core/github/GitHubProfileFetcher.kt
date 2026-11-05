package com.osrs.careerguild.core.github

import com.osrs.careerguild.BuildConfig
import com.osrs.careerguild.core.profile.UserProfileManager
import com.osrs.careerguild.data.local.dao.GitHubRepoCacheDao
import com.osrs.careerguild.data.local.dao.GitHubRepoDao
import com.osrs.careerguild.data.local.entity.GitHubRepoCacheEntity
import com.osrs.careerguild.data.local.entity.GitHubRepoEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Clean domain representation of a candidate's public GitHub portfolio project.
 */
data class GitHubRepo(
    val id: Long,
    val name: String,
    val description: String?,
    val htmlUrl: String,
    val topics: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val pushedAt: String? = null,
    val readmeExcerpt: String? = null,
)

@Serializable
private data class RawGitHubRepoDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("html_url") val htmlUrl: String,
    val topics: List<String> = emptyList(),
    val language: String? = null,
    @SerialName("pushed_at") val pushedAt: String? = null,
)

/**
 * Fetches and caches candidate public GitHub repository context.
 *
 * Directives:
 * - Reads username dynamically from [UserProfileManager]. Zero hardcoded usernames.
 * - Filters out forks and archived repositories.
 * - Extracts topics, primary & secondary languages, and recency timestamps.
 * - For top 6 repositories, extracts first 1500 characters of README.
 * - Honors GitHub API rate limits (60 req/hr unauth): uses ETag / If-None-Match and 6-hour Room cache.
 * - Uses optional GITHUB_TOKEN from BuildConfig when available.
 * - Fail-safe: Returns cached data or empty list on rate limit or user not found. Tailoring never blocks.
 */
class GitHubProfileFetcher(
    private val httpClient: HttpClient,
    private val userProfileManager: UserProfileManager,
    private val gitHubRepoDao: GitHubRepoDao,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val gitHubRepoCacheDao: GitHubRepoCacheDao? = null,
) {

    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
        const val TOP_REPOS_COUNT = 8
        const val MAX_README_CHARS = 1500
    }

    suspend fun fetchRepos(forceRefresh: Boolean = false): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val userProfile = userProfileManager.profile.value
        val username = userProfile.effectiveGithubUsername

        if (username.isBlank()) {
            return@withContext gitHubRepoDao.getAll().map { it.toDomain() }
        }

        val cachedEntities = gitHubRepoDao.getAll()
        val now = System.currentTimeMillis()
        val isCacheValid = cachedEntities.isNotEmpty() && (now - cachedEntities.first().cachedAt < CACHE_TTL_MS)

        if (!forceRefresh && isCacheValid) {
            return@withContext cachedEntities.map { it.toDomain() }
        }

        val primaryEtag = cachedEntities.firstOrNull()?.etag

        runCatching {
            val reposUrl = "https://api.github.com/users/$username/repos?sort=pushed&per_page=100"
            val response = httpClient.get(reposUrl) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "CareerGuildApp/1.0 (Android)")
                if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GITHUB_TOKEN}")
                }
                if (!primaryEtag.isNullOrBlank()) {
                    header(HttpHeaders.IfNoneMatch, primaryEtag)
                }
            }

            if (response.status == HttpStatusCode.NotModified) {
                android.util.Log.i("GitHubFetcher", "GitHub repos not modified (304), using cache.")
                return@runCatching cachedEntities.map { it.toDomain() }
            }

            if (response.status != HttpStatusCode.OK) {
                android.util.Log.w("GitHubFetcher", "GitHub API returned ${response.status.value}: using cache fallback.")
                return@runCatching cachedEntities.map { it.toDomain() }
            }

            val newEtag = response.headers[HttpHeaders.ETag]
            val responseBody = response.body<String>()
            val rawDtos = json.decodeFromString<List<RawGitHubRepoDto>>(responseBody)

            val filteredDtos = rawDtos
                .filter { !it.fork && !it.archived }
                .sortedByDescending { it.pushedAt ?: "" }

            val topRepos = filteredDtos.take(TOP_REPOS_COUNT)
            val entities = filteredDtos.map { dto ->
                val isTop = topRepos.any { it.id == dto.id }
                val languages = if (isTop) {
                    fetchRepoLanguages(username, dto.name, dto.language)
                } else {
                    listOfNotNull(dto.language)
                }

                val readme = if (isTop) {
                    fetchReadmeExcerpt(username, dto.name)
                } else null

                GitHubRepoEntity(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description,
                    htmlUrl = dto.htmlUrl,
                    topics = dto.topics.joinToString(","),
                    languages = languages.joinToString(","),
                    pushedAt = dto.pushedAt,
                    readmeExcerpt = readme,
                    etag = newEtag,
                    cachedAt = now,
                )
            }

            if (entities.isNotEmpty()) {
                gitHubRepoDao.clear()
                gitHubRepoDao.upsertAll(entities)
                gitHubRepoCacheDao?.let { cacheDao ->
                    val cacheEntities = entities.take(TOP_REPOS_COUNT).map { ent ->
                        GitHubRepoCacheEntity(
                            repoId = ent.id,
                            username = username,
                            name = ent.name,
                            description = ent.description,
                            htmlUrl = ent.htmlUrl,
                            topics = ent.topics,
                            languages = ent.languages,
                            pushedAt = ent.pushedAt,
                            readmeExcerpt = ent.readmeExcerpt,
                            etag = ent.etag,
                            cachedAt = ent.cachedAt,
                        )
                    }
                    cacheDao.clear()
                    cacheDao.upsertAll(cacheEntities)
                }
            }

            entities.map { it.toDomain() }
        }.getOrElse { error ->
            android.util.Log.w("GitHubFetcher", "Failed to fetch GitHub repos: ${error.message}")
            cachedEntities.map { it.toDomain() }
        }
    }

    private suspend fun fetchRepoLanguages(user: String, repoName: String, fallback: String?): List<String> {
        return runCatching {
            val url = "https://api.github.com/repos/$user/$repoName/languages"
            val response = httpClient.get(url) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "CareerGuildApp/1.0 (Android)")
                if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GITHUB_TOKEN}")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val jsonStr = response.body<String>()
                val obj = json.decodeFromString<JsonObject>(jsonStr)
                obj.keys.toList()
            } else {
                listOfNotNull(fallback)
            }
        }.getOrDefault(listOfNotNull(fallback))
    }

    private suspend fun fetchReadmeExcerpt(user: String, repoName: String): String? {
        return runCatching {
            val apiReadmeUrl = "https://api.github.com/repos/$user/$repoName/readme"
            val apiRes = httpClient.get(apiReadmeUrl) {
                header(HttpHeaders.Accept, "application/vnd.github.raw+json")
                header(HttpHeaders.UserAgent, "CareerGuildApp/1.0 (Android)")
                if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GITHUB_TOKEN}")
                }
            }
            if (apiRes.status == HttpStatusCode.OK) {
                return@runCatching apiRes.body<String>().take(MAX_README_CHARS)
            }
            val rawUrl = "https://raw.githubusercontent.com/$user/$repoName/HEAD/README.md"
            val response = httpClient.get(rawUrl) {
                header(HttpHeaders.UserAgent, "CareerGuildApp/1.0 (Android)")
            }
            if (response.status == HttpStatusCode.OK) {
                response.body<String>().take(MAX_README_CHARS)
            } else null
        }.getOrNull()
    }

    private fun GitHubRepoEntity.toDomain(): GitHubRepo {
        return GitHubRepo(
            id = id,
            name = name,
            description = description,
            htmlUrl = htmlUrl,
            topics = if (topics.isBlank()) emptyList() else topics.split(",").map { it.trim() },
            languages = if (languages.isBlank()) emptyList() else languages.split(",").map { it.trim() },
            pushedAt = pushedAt,
            readmeExcerpt = readmeExcerpt,
        )
    }
}
