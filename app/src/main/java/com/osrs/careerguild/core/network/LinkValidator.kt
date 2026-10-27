package com.osrs.careerguild.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

enum class LinkValidationStatus {
    VERIFIED,
    TRUSTED_UNVERIFIED,
    UNRESOLVED,
    DEAD,
    SEED,
}

data class LinkValidationOutcome(
    val status: LinkValidationStatus,
    val finalUrl: String,
    val httpStatusCode: Int = 200,
    val message: String = "",
)

/**
 * LinkValidator — Courier Guild & Bounty Board Direct Link Verification.
 *
 * Implements Section 3 & Task 6 Directives:
 * - Specific posting validation (no homepages or generic portals).
 * - Multi-hop redirect following (up to 5 hops, essential for Adzuna redirect links).
 * - HEAD verification with 405 fallback to partial GET (bytes=0-1024).
 * - Status classification: VERIFIED, TRUSTED_UNVERIFIED, UNRESOLVED, DEAD, SEED.
 * - 24-hour verification cache to prevent redundant HTTP pings.
 */
object LinkValidator {

    private val TRUSTED_ATS_DOMAINS = listOf(
        "linkedin.com",
        "greenhouse.io",
        "lever.co",
        "ashbyhq.com",
        "myworkdayjobs.com",
        "workday.com",
        "remotive.com",
        "arbeitnow.com",
        "simplify.jobs",
        "careers.google.com",
        "openai.com",
        "anthropic.com",
        "stripe.com",
        "netflix.com",
        "databricks.com",
        "apple.com",
        "amazon.jobs",
        "metacareers.com",
        "palantir.com",
        "snowflake.com",
        "uber.com",
        "airbnb.com",
        "janestreet.com",
        "scale.com"
    )

    private val cache = ConcurrentHashMap<String, Pair<LinkValidationOutcome, Long>>()
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class ValidationResult(
        val url: String,
        val isValid: Boolean,
        val httpStatusCode: Int = 200,
        val domain: String,
        val message: String
    )

    /**
     * Checks link structure and domain validity.
     */
    fun checkStructure(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val trimmed = rawUrl.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            trimmed.length > 8 &&
            trimmed.substringAfter("://").substringBefore("/").isNotBlank()
    }

    /**
     * Confirms that a link points to a concrete requisition and not a generic homepage or job portal root.
     */
    fun isSpecificPosting(rawUrl: String): Boolean {
        if (!checkStructure(rawUrl)) return false
        val url = rawUrl.lowercase().trim()
        val isGenericPortal = url.endsWith(".com") || url.endsWith(".com/") ||
            url.endsWith(".io") || url.endsWith(".io/") ||
            url.endsWith("/careers") || url.endsWith("/careers/") ||
            url.endsWith("/jobs") || url.endsWith("/jobs/")
        if (isGenericPortal) return false

        val deepRequisitionRegex = Regex(
            """(greenhouse\.io/[^/]+/jobs/\d+|jobs\.lever\.co/[^/]+/[a-f0-9-]+|ashbyhq\.com/[^/]+/[a-f0-9-]+|myworkdayjobs\.com/[^/]+/job/|linkedin\.com/jobs/view/\d+|remotive\.com/remote-jobs/[^/]+|arbeitnow\.com/view/[^/]+|simplify\.jobs/p/[^/]+)"""
        )
        return deepRequisitionRegex.containsMatchIn(url) || url.count { it == '/' } >= 4
    }

    /**
     * Deeply validates a direct link using redirect resolution (up to 5 hops) and cached results (24h).
     */
    suspend fun resolveAndValidateDirectLink(rawUrl: String): LinkValidationOutcome = withContext(Dispatchers.IO) {
        if (!checkStructure(rawUrl)) {
            return@withContext LinkValidationOutcome(
                status = LinkValidationStatus.DEAD,
                finalUrl = rawUrl,
                httpStatusCode = 400,
                message = "Invalid URL syntax",
            )
        }

        val now = System.currentTimeMillis()
        cache[rawUrl]?.let { (cachedOutcome, timestamp) ->
            if (now - timestamp < CACHE_TTL_MS) {
                return@withContext cachedOutcome
            }
        }

        var currentUrl = rawUrl
        var hops = 0
        val maxHops = 5
        var lastStatus = 200

        try {
            while (hops < maxHops) {
                hops++
                val parsed = URL(currentUrl)
                val host = parsed.host.lowercase()
                val isTrusted = TRUSTED_ATS_DOMAINS.any { host.contains(it) }

                val connection = (parsed.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 4000
                    readTimeout = 4000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", PoliteHttpClient.USER_AGENT)
                }

                var code = connection.responseCode

                // If HEAD returns 405 Method Not Allowed, fallback to partial GET
                if (code == HttpURLConnection.HTTP_BAD_METHOD) {
                    connection.disconnect()
                    val fallbackConn = (parsed.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 4000
                        readTimeout = 4000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", PoliteHttpClient.USER_AGENT)
                        setRequestProperty("Range", "bytes=0-1024")
                    }
                    code = fallbackConn.responseCode
                    fallbackConn.disconnect()
                } else {
                    connection.disconnect()
                }

                lastStatus = code

                // Check for redirect (301, 302, 303, 307, 308)
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(parsed, location).toString()
                        }
                        continue
                    }
                }

                // Final destination handling
                val outcome = when {
                    code in 200..299 -> {
                        LinkValidationOutcome(
                            status = LinkValidationStatus.VERIFIED,
                            finalUrl = currentUrl,
                            httpStatusCode = code,
                            message = "Requisition verified (HTTP $code)",
                        )
                    }
                    code in listOf(401, 403) && isTrusted -> {
                        LinkValidationOutcome(
                            status = LinkValidationStatus.TRUSTED_UNVERIFIED,
                            finalUrl = currentUrl,
                            httpStatusCode = code,
                            message = "Trusted ATS protected by WAF/anti-bot challenge (HTTP $code)",
                        )
                    }
                    code in listOf(404, 410) -> {
                        LinkValidationOutcome(
                            status = LinkValidationStatus.DEAD,
                            finalUrl = currentUrl,
                            httpStatusCode = code,
                            message = "Requisition dead or expired (HTTP $code)",
                        )
                    }
                    else -> {
                        if (isTrusted) {
                            LinkValidationOutcome(
                                status = LinkValidationStatus.TRUSTED_UNVERIFIED,
                                finalUrl = currentUrl,
                                httpStatusCode = code,
                                message = "Trusted ATS returned HTTP $code",
                            )
                        } else {
                            LinkValidationOutcome(
                                status = LinkValidationStatus.DEAD,
                                finalUrl = currentUrl,
                                httpStatusCode = code,
                                message = "Unreachable endpoint (HTTP $code)",
                            )
                        }
                    }
                }
                cache[rawUrl] = outcome to now
                return@withContext outcome
            }

            val unresolved = LinkValidationOutcome(
                status = LinkValidationStatus.UNRESOLVED,
                finalUrl = currentUrl,
                httpStatusCode = lastStatus,
                message = "Redirect chain exceeded $maxHops hops",
            )
            cache[rawUrl] = unresolved to now
            unresolved
        } catch (e: Exception) {
            val host = runCatching { URL(rawUrl).host.lowercase() }.getOrDefault("")
            val isTrusted = TRUSTED_ATS_DOMAINS.any { host.contains(it) }
            val outcome = if (isTrusted && isSpecificPosting(rawUrl)) {
                LinkValidationOutcome(
                    status = LinkValidationStatus.TRUSTED_UNVERIFIED,
                    finalUrl = rawUrl,
                    httpStatusCode = 200,
                    message = "Trusted ATS offline/timeout check (${e.message})",
                )
            } else {
                LinkValidationOutcome(
                    status = LinkValidationStatus.DEAD,
                    finalUrl = rawUrl,
                    httpStatusCode = 0,
                    message = "Network error: ${e.message}",
                )
            }
            cache[rawUrl] = outcome to now
            outcome
        }
    }

    /**
     * Backward-compatible link connectivity check on [Dispatchers.IO].
     */
    suspend fun validateUrl(rawUrl: String): ValidationResult = withContext(Dispatchers.IO) {
        val outcome = resolveAndValidateDirectLink(rawUrl)
        val host = runCatching { URL(outcome.finalUrl).host.lowercase() }.getOrDefault("")
        ValidationResult(
            url = outcome.finalUrl,
            isValid = outcome.status == LinkValidationStatus.VERIFIED || outcome.status == LinkValidationStatus.TRUSTED_UNVERIFIED,
            httpStatusCode = outcome.httpStatusCode,
            domain = host,
            message = outcome.message,
        )
    }
}
