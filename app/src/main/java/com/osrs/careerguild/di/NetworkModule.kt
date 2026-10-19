package com.osrs.careerguild.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates the Ktor [HttpClient] used by [TechRadarScraperImpl].
 *
 * Configuration:
 *  - Engine: CIO (Coroutine I/O — no OkHttp dependency, leaner binary)
 *  - ContentNegotiation: kotlinx.serialization JSON with permissive config
 *    so partial/evolving GitHub feed schemas don't crash the scraper
 *  - Timeouts: 30s request, 15s connect (GitHub CDN raw files can be slow)
 *
 * Node: TECH_RADAR_SCRAPER
 */
object NetworkModule {

    fun createHttpClient(): HttpClient = HttpClient(CIO) {

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true   // feed schemas evolve — don't crash
                    coerceInputValues  = true   // null-safe for partial records
                    isLenient          = true   // handle trailing commas in some feeds
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis  = 30_000L
        }
    }
}
