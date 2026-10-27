package com.osrs.careerguild.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * PoliteHttpClient — Respectful, rate-limited HTTP client wrapper.
 *
 * Enforces:
 * 1. Global concurrency limit: max 6 requests in flight across all hosts.
 * 2. Per-host concurrency limit: max 2 requests in flight to the same host simultaneously.
 * 3. Per-host request jitter: 300ms to 800ms spacing between consecutive calls to the same host.
 * 4. Backoff: on HTTP 429 or 503, checks Retry-After header (capped at 60s) or backs off 2s, 4s, 8s (up to 3 attempts).
 * 5. Circuit breaker: 3 consecutive 4xx/5xx failures (excluding 404) opens circuit for 15 minutes.
 * 6. Honest User-Agent: CareerGuildApp/1.0 (Android; https://github.com/osrs-career-guild).
 */
class PoliteHttpClient(
    val httpClient: HttpClient,
) {
    companion object {
        const val USER_AGENT = "CareerGuildApp/1.0 (Android; https://github.com/osrs-career-guild)"
        private const val MAX_GLOBAL_CONCURRENCY = 6
        private const val MAX_HOST_CONCURRENCY = 2
        private const val CIRCUIT_BREAKER_DURATION_MS = 15 * 60 * 1000L // 15 mins
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    private val globalSemaphore = Semaphore(MAX_GLOBAL_CONCURRENCY)
    private val hostSemaphores = ConcurrentHashMap<String, Semaphore>()
    private val hostLastRequestTimestamp = ConcurrentHashMap<String, Long>()
    private val hostConsecutiveFailures = ConcurrentHashMap<String, Int>()
    private val hostCircuitOpenUntil = ConcurrentHashMap<String, Long>()

    private fun getHost(urlString: String): String {
        return runCatching {
            URLBuilder(urlString).host
        }.getOrDefault("default_host")
    }

    private fun getHostSemaphore(host: String): Semaphore {
        return hostSemaphores.computeIfAbsent(host) { Semaphore(MAX_HOST_CONCURRENCY) }
    }

    private fun checkCircuitBreaker(host: String) {
        val openUntil = hostCircuitOpenUntil[host] ?: 0L
        val now = System.currentTimeMillis()
        if (now < openUntil) {
            val remainingSec = (openUntil - now) / 1000
            throw IOException("Circuit breaker open for host '$host'. Fast-failing for $remainingSec more seconds.")
        }
    }

    private fun recordSuccess(host: String) {
        hostConsecutiveFailures[host] = 0
        hostCircuitOpenUntil.remove(host)
    }

    private fun recordFailure(host: String, statusCode: HttpStatusCode?) {
        // 404 Not Found is normal for probe requests — do NOT trip circuit breaker on 404
        if (statusCode == HttpStatusCode.NotFound) return

        val count = (hostConsecutiveFailures[host] ?: 0) + 1
        hostConsecutiveFailures[host] = count
        if (count >= MAX_CONSECUTIVE_FAILURES) {
            val openUntil = System.currentTimeMillis() + CIRCUIT_BREAKER_DURATION_MS
            hostCircuitOpenUntil[host] = openUntil
            android.util.Log.w("PoliteHttpClient", "Circuit breaker tripped for host '$host'! Open for 15 minutes.")
        }
    }

    private suspend fun throttleHost(host: String) {
        val lastTime = hostLastRequestTimestamp[host] ?: 0L
        val now = System.currentTimeMillis()
        val elapsed = now - lastTime
        val desiredDelay = Random.nextLong(300, 801)
        if (elapsed < desiredDelay) {
            delay(desiredDelay - elapsed)
        }
        hostLastRequestTimestamp[host] = System.currentTimeMillis()
    }

    suspend fun get(
        urlString: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val host = getHost(urlString)
        checkCircuitBreaker(host)

        val hostSemaphore = getHostSemaphore(host)
        var attempt = 0
        val maxAttempts = 3
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            var shouldRetry = false
            var retryDelayMs = 0L

            try {
                val response: HttpResponse = globalSemaphore.withPermit {
                    hostSemaphore.withPermit {
                        throttleHost(host)
                        httpClient.get(urlString) {
                            header(HttpHeaders.UserAgent, USER_AGENT)
                            block()
                        }
                    }
                }

                if (response.status == HttpStatusCode.TooManyRequests || response.status == HttpStatusCode.ServiceUnavailable) {
                    recordFailure(host, response.status)
                    if (attempt < maxAttempts) {
                        val retryAfterSec = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                        retryDelayMs = if (retryAfterSec != null && retryAfterSec > 0) {
                            minOf(retryAfterSec * 1000L, 60_000L)
                        } else {
                            (1L shl attempt) * 1000L
                        }
                        shouldRetry = true
                    } else {
                        return response
                    }
                } else if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
                    recordFailure(host, response.status)
                    return response
                } else {
                    recordSuccess(host)
                    return response
                }
            } catch (e: Exception) {
                lastException = e
                recordFailure(host, null)
                if (attempt < maxAttempts) {
                    shouldRetry = true
                    retryDelayMs = (1L shl attempt) * 1000L
                }
            }

            if (shouldRetry && retryDelayMs > 0) {
                android.util.Log.w("PoliteHttpClient", "Backing off ${retryDelayMs}ms on $host (attempt $attempt/$maxAttempts)")
                delay(retryDelayMs)
            }
        }

        throw lastException ?: IOException("Failed request to $urlString after $maxAttempts attempts")
    }

    suspend fun head(
        urlString: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val host = getHost(urlString)
        checkCircuitBreaker(host)

        val hostSemaphore = getHostSemaphore(host)
        val response = globalSemaphore.withPermit {
            hostSemaphore.withPermit {
                throttleHost(host)
                httpClient.head(urlString) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    block()
                }
            }
        }
        if (response.status.isSuccess() || response.status == HttpStatusCode.NotFound) {
            recordSuccess(host)
        } else {
            recordFailure(host, response.status)
        }
        return response
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
}
