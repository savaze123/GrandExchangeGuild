package com.osrs.careerguild.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates the Ktor [HttpClient] used by [TechRadarScraperImpl].
 *
 * Configuration:
 *  - Engine: CIO (Coroutine I/O — zero OkHttp bloat)
 *  - ContentNegotiation: kotlinx.serialization JSON registered for Application.Json,
 *    Text.Plain (for raw.githubusercontent.com feeds), and Any to guarantee reliable live job ingestion.
 *  - Timeouts: 30s request, 15s connect
 *
 * Node: TECH_RADAR_SCRAPER
 */
object NetworkModule {

    fun createHttpClient(): HttpClient = HttpClient(CIO) {

        install(ContentNegotiation) {
            val lenientJson = Json {
                ignoreUnknownKeys = true   // feed schemas evolve — don't crash
                coerceInputValues  = true   // null-safe for partial records
                isLenient          = true   // handle trailing commas in some feeds
            }
            json(lenientJson, ContentType.Application.Json)
            json(lenientJson, ContentType.Text.Plain)
            json(lenientJson, ContentType.Any)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis  = 30_000L
        }
    }
}
