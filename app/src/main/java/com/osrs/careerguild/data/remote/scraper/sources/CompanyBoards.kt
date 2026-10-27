package com.osrs.careerguild.data.remote.scraper.sources

import com.osrs.careerguild.core.network.PoliteHttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

enum class BoardType {
    GREENHOUSE,
    LEVER,
    ASHBY,
}

data class CompanyBoardSlug(
    val slug: String,
    val companyName: String,
    val boardType: BoardType,
) {
    val endpointUrl: String
        get() = when (boardType) {
            BoardType.GREENHOUSE -> "https://boards-api.greenhouse.io/v1/boards/$slug/jobs"
            BoardType.LEVER -> "https://api.lever.co/v0/postings/$slug?mode=json"
            BoardType.ASHBY -> "https://api.ashbyhq.com/posting-api/job-board/$slug"
        }
}

/**
 * Curated directory of 55+ tech companies in NYC Metro (NY, Long Island, NJ, CT) and remote-first
 * hiring Software, AI, Systems, and Data Engineers.
 */
object CompanyBoards {

    val CURATED_BOARDS: List<CompanyBoardSlug> = listOf(
        // --- Greenhouse Endpoints (NYC Metro / Tier-1 Remote) ---
        CompanyBoardSlug("datadog", "Datadog", BoardType.GREENHOUSE),
        CompanyBoardSlug("mongodb", "MongoDB", BoardType.GREENHOUSE),
        CompanyBoardSlug("squarespace", "Squarespace", BoardType.GREENHOUSE),
        CompanyBoardSlug("spotify", "Spotify", BoardType.GREENHOUSE),
        CompanyBoardSlug("etsy", "Etsy", BoardType.GREENHOUSE),
        CompanyBoardSlug("flatironhealth", "Flatiron Health", BoardType.GREENHOUSE),
        CompanyBoardSlug("oscar", "Oscar Health", BoardType.GREENHOUSE),
        CompanyBoardSlug("cockroachlabs", "Cockroach Labs", BoardType.GREENHOUSE),
        CompanyBoardSlug("cloudflare", "Cloudflare", BoardType.GREENHOUSE),
        CompanyBoardSlug("stripe", "Stripe", BoardType.GREENHOUSE),
        CompanyBoardSlug("canonical", "Canonical", BoardType.GREENHOUSE),
        CompanyBoardSlug("supabase", "Supabase", BoardType.GREENHOUSE),
        CompanyBoardSlug("twilio", "Twilio", BoardType.GREENHOUSE),
        CompanyBoardSlug("reddit", "Reddit", BoardType.GREENHOUSE),
        CompanyBoardSlug("pinterest", "Pinterest", BoardType.GREENHOUSE),
        CompanyBoardSlug("doordash", "DoorDash", BoardType.GREENHOUSE),
        CompanyBoardSlug("airbnb", "Airbnb", BoardType.GREENHOUSE),
        CompanyBoardSlug("github", "GitHub", BoardType.GREENHOUSE),
        CompanyBoardSlug("discord", "Discord", BoardType.GREENHOUSE),
        CompanyBoardSlug("instacart", "Instacart", BoardType.GREENHOUSE),
        CompanyBoardSlug("robinhood", "Robinhood", BoardType.GREENHOUSE),
        CompanyBoardSlug("coinbase", "Coinbase", BoardType.GREENHOUSE),
        CompanyBoardSlug("figma", "Figma", BoardType.GREENHOUSE),
        CompanyBoardSlug("webflow", "Webflow", BoardType.GREENHOUSE),
        CompanyBoardSlug("braze", "Braze", BoardType.GREENHOUSE),
        CompanyBoardSlug("seatgeek", "SeatGeek", BoardType.GREENHOUSE),
        CompanyBoardSlug("warbyparker", "Warby Parker", BoardType.GREENHOUSE),
        CompanyBoardSlug("onepeloton", "Peloton", BoardType.GREENHOUSE),
        CompanyBoardSlug("chainalysis", "Chainalysis", BoardType.GREENHOUSE),
        CompanyBoardSlug("vanta", "Vanta", BoardType.GREENHOUSE),
        CompanyBoardSlug("scaleai", "Scale AI", BoardType.GREENHOUSE),
        CompanyBoardSlug("postman", "Postman", BoardType.GREENHOUSE),
        CompanyBoardSlug("carta", "Carta", BoardType.GREENHOUSE),
        CompanyBoardSlug("rippling", "Rippling", BoardType.GREENHOUSE),
        CompanyBoardSlug("hashicorp", "HashiCorp", BoardType.GREENHOUSE),
        CompanyBoardSlug("betterment", "Betterment", BoardType.GREENHOUSE),

        // --- Lever Endpoints (NYC Metro / Tech Hubs) ---
        CompanyBoardSlug("palantir", "Palantir", BoardType.LEVER),
        CompanyBoardSlug("ramp", "Ramp", BoardType.LEVER),
        CompanyBoardSlug("brex", "Brex", BoardType.LEVER),
        CompanyBoardSlug("vercel", "Vercel", BoardType.LEVER),
        CompanyBoardSlug("netflix", "Netflix", BoardType.LEVER),
        CompanyBoardSlug("klaviyo", "Klaviyo", BoardType.LEVER),
        CompanyBoardSlug("atlassian", "Atlassian", BoardType.LEVER),
        CompanyBoardSlug("yelp", "Yelp", BoardType.LEVER),
        CompanyBoardSlug("docker", "Docker", BoardType.LEVER),
        CompanyBoardSlug("gitlab", "GitLab", BoardType.LEVER),
        CompanyBoardSlug("notion", "Notion", BoardType.LEVER),
        CompanyBoardSlug("retool", "Retool", BoardType.LEVER),

        // --- Ashby Endpoints (AI & Next-Gen Developer Platforms) ---
        CompanyBoardSlug("openai", "OpenAI", BoardType.ASHBY),
        CompanyBoardSlug("anthropic", "Anthropic", BoardType.ASHBY),
        CompanyBoardSlug("linear", "Linear", BoardType.ASHBY),
        CompanyBoardSlug("perplexity", "Perplexity", BoardType.ASHBY),
        CompanyBoardSlug("anysphere", "Cursor (Anysphere)", BoardType.ASHBY),
        CompanyBoardSlug("replit", "Replit", BoardType.ASHBY),
        CompanyBoardSlug("together-ai", "Together AI", BoardType.ASHBY),
        CompanyBoardSlug("cohere", "Cohere", BoardType.ASHBY),
        CompanyBoardSlug("mistral", "Mistral AI", BoardType.ASHBY),
        CompanyBoardSlug("elevenlabs", "ElevenLabs", BoardType.ASHBY),
    )

    private val probedCache = ConcurrentHashMap<String, Boolean>()

    /**
     * Probes candidate boards concurrently using [PoliteHttpClient].
     * Retains endpoints returning HTTP 200 with non-empty payload, drops 404s.
     */
    suspend fun probeAndFilterActiveBoards(
        client: PoliteHttpClient,
        boards: List<CompanyBoardSlug> = CURATED_BOARDS,
    ): List<CompanyBoardSlug> = coroutineScope {
        boards.map { board ->
            async {
                val cached = probedCache[board.slug]
                if (cached != null) {
                    return@async if (cached) board else null
                }

                try {
                    val response = client.get(board.endpointUrl)
                    if (response.status == HttpStatusCode.OK) {
                        val body = response.bodyAsText()
                        // Ensure it has at least 1 job posting
                        val hasJobs = body.contains("\"id\"") || body.contains("\"title\"") || body.contains("\"text\"")
                        probedCache[board.slug] = hasJobs
                        if (hasJobs) board else null
                    } else {
                        probedCache[board.slug] = false
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.d("CompanyBoards", "Probe failed for ${board.companyName} (${board.slug}): ${e.message}")
                    probedCache[board.slug] = false
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }
}
