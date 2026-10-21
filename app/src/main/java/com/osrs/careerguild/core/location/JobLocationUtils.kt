package com.osrs.careerguild.core.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic utilities for job location and distance computation.
 * Supports geodesic distance calculation from user ZIP code to job cities/metro regions,
 * formatting distance tags on Quest cards, and active radius/work-mode filtering.
 */
object JobLocationUtils {

    data class GeoCoordinates(val lat: Double, val lon: Double)

    // Known city/metro coordinate anchors
    private val cityCoordinates = mapOf(
        "new york" to GeoCoordinates(40.7589, -73.9851),
        "nyc" to GeoCoordinates(40.7589, -73.9851),
        "manhattan" to GeoCoordinates(40.7589, -73.9851),
        "brooklyn" to GeoCoordinates(40.6782, -73.9442),
        "queens" to GeoCoordinates(40.7282, -73.7949),
        "san francisco" to GeoCoordinates(37.7749, -122.4194),
        "sf" to GeoCoordinates(37.7749, -122.4194),
        "seattle" to GeoCoordinates(47.6062, -122.3321),
        "austin" to GeoCoordinates(30.2672, -97.7431),
        "melbourne" to GeoCoordinates(28.0836, -80.6081),
        "warren" to GeoCoordinates(42.5145, -83.0147),
        "detroit" to GeoCoordinates(42.3314, -83.0458),
        "boise" to GeoCoordinates(43.6150, -116.2023),
        "boston" to GeoCoordinates(42.3601, -71.0589),
        "chicago" to GeoCoordinates(41.8781, -87.6298),
        "los angeles" to GeoCoordinates(34.0522, -118.2437),
        "jersey city" to GeoCoordinates(40.7178, -74.0431),
        "philadelphia" to GeoCoordinates(39.9526, -75.1652),
    )

    // Specific known ZIP codes (including user's test ZIPs: 11581, 11365, 10001)
    private val specificZipCoordinates = mapOf(
        "11581" to GeoCoordinates(40.6657, -73.7093), // Valley Stream / Long Island border
        "11365" to GeoCoordinates(40.7380, -73.7950), // Fresh Meadows / Queens
        "10001" to GeoCoordinates(40.7501, -73.9996), // Chelsea / Midtown NYC
        "11201" to GeoCoordinates(40.6958, -73.9897), // Downtown Brooklyn
        "10451" to GeoCoordinates(40.8208, -73.9234), // South Bronx
        "07030" to GeoCoordinates(40.7439, -74.0324), // Hoboken, NJ
        "94103" to GeoCoordinates(37.7726, -122.4099), // SoMa San Francisco
        "98101" to GeoCoordinates(47.6101, -122.3344), // Seattle Downtown
        "78701" to GeoCoordinates(30.2672, -97.7431), // Austin Downtown
        "32901" to GeoCoordinates(28.0836, -80.6081), // Melbourne, FL
        "48088" to GeoCoordinates(42.5145, -83.0147), // Warren, MI
        "83702" to GeoCoordinates(43.6150, -116.2023), // Boise, ID
    )

    /**
     * Resolves coordinates from a ZIP code string.
     * Checks exact ZIP match first, then 3-digit prefix, then 1-digit regional fallback.
     */
    fun resolveZipCoordinates(zip: String): GeoCoordinates {
        val cleanZip = zip.filter { it.isDigit() }.take(5)
        if (cleanZip.isBlank()) return GeoCoordinates(40.7589, -73.9851) // Default to NYC

        specificZipCoordinates[cleanZip]?.let { return it }

        // 3-digit prefix lookup
        if (cleanZip.length >= 3) {
            val prefix3 = cleanZip.take(3).toIntOrNull()
            if (prefix3 != null) {
                when (prefix3) {
                    in 100..104 -> return GeoCoordinates(40.7589, -73.9851) // Manhattan / Bronx
                    in 110..118 -> return GeoCoordinates(40.7128, -73.6500) // Queens / Nassau Long Island
                    in 112..112 -> return GeoCoordinates(40.6782, -73.9442) // Brooklyn
                    in 70..89   -> return GeoCoordinates(40.7357, -74.1724) // New Jersey
                    in 10..27   -> return GeoCoordinates(42.3601, -71.0589) // Massachusetts / Boston
                    in 190..191 -> return GeoCoordinates(39.9526, -75.1652) // Philadelphia
                    in 200..205 -> return GeoCoordinates(38.9072, -77.0369) // Washington DC
                    in 300..319 -> return GeoCoordinates(33.7490, -84.3880) // Atlanta, GA
                    in 320..349 -> return GeoCoordinates(28.0836, -80.6081) // Florida (Melbourne/Orlando)
                    in 480..483 -> return GeoCoordinates(42.5145, -83.0147) // Michigan / Detroit
                    in 600..608 -> return GeoCoordinates(41.8781, -87.6298) // Chicago, IL
                    in 750..753 -> return GeoCoordinates(32.7767, -96.7970) // Dallas, TX
                    in 786..787 -> return GeoCoordinates(30.2672, -97.7431) // Austin, TX
                    in 800..805 -> return GeoCoordinates(39.7392, -104.9903) // Denver, CO
                    in 837..838 -> return GeoCoordinates(43.6150, -116.2023) // Boise, ID
                    in 940..946 -> return GeoCoordinates(37.7749, -122.4194) // San Francisco Bay Area
                    in 980..984 -> return GeoCoordinates(47.6062, -122.3321) // Seattle, WA
                    in 900..918 -> return GeoCoordinates(34.0522, -118.2437) // Los Angeles, CA
                }
            }
        }

        // 1-digit broad regional fallback
        return when (cleanZip.firstOrNull()) {
            '0' -> GeoCoordinates(40.7439, -74.0324) // Northeast / NJ
            '1' -> GeoCoordinates(40.7589, -73.9851) // New York State
            '2' -> GeoCoordinates(38.9072, -77.0369) // Mid-Atlantic
            '3' -> GeoCoordinates(28.5383, -81.3792) // Southeast
            '4' -> GeoCoordinates(42.3314, -83.0458) // Great Lakes
            '5' -> GeoCoordinates(44.9778, -93.2650) // Midwest
            '6' -> GeoCoordinates(41.8781, -87.6298) // Central / Chicago
            '7' -> GeoCoordinates(30.2672, -97.7431) // South / Texas
            '8' -> GeoCoordinates(39.7392, -104.9903) // Mountain West
            '9' -> GeoCoordinates(37.7749, -122.4194) // West Coast / CA
            else -> GeoCoordinates(40.7589, -73.9851) // Default NYC
        }
    }

    /**
     * Resolves coordinates from a job's location string.
     */
    fun resolveJobCoordinates(location: String): GeoCoordinates {
        val lower = location.lowercase()
        for ((city, coords) in cityCoordinates) {
            if (lower.contains(city)) return coords
        }
        // State lookups
        if (lower.contains("ny") || lower.contains("new york")) return GeoCoordinates(40.7589, -73.9851)
        if (lower.contains("ca") || lower.contains("california")) return GeoCoordinates(37.7749, -122.4194)
        if (lower.contains("wa") || lower.contains("washington")) return GeoCoordinates(47.6062, -122.3321)
        if (lower.contains("tx") || lower.contains("texas")) return GeoCoordinates(30.2672, -97.7431)
        if (lower.contains("fl") || lower.contains("florida")) return GeoCoordinates(28.0836, -80.6081)
        if (lower.contains("mi") || lower.contains("michigan")) return GeoCoordinates(42.5145, -83.0147)
        if (lower.contains("id") || lower.contains("idaho")) return GeoCoordinates(43.6150, -116.2023)
        if (lower.contains("il") || lower.contains("illinois")) return GeoCoordinates(41.8781, -87.6298)
        if (lower.contains("ma") || lower.contains("massachusetts")) return GeoCoordinates(42.3601, -71.0589)

        return GeoCoordinates(40.7589, -73.9851)
    }

    /**
     * Calculates distance between two coordinates in statute miles using the Haversine formula.
     */
    fun calculateDistanceMiles(coord1: GeoCoordinates, coord2: GeoCoordinates): Int {
        val r = 3958.8 // Radius of the earth in statute miles
        val latDistance = Math.toRadians(coord2.lat - coord1.lat)
        val lonDistance = Math.toRadians(coord2.lon - coord1.lon)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(coord1.lat)) * cos(Math.toRadians(coord2.lat)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toInt()
    }

    /**
     * Formats the location and distance badge on job listing cards.
     * e.g., "New York, NY • 15 mi away" or "San Francisco, CA • Remote"
     */
    fun formatLocationAndDistance(jobLocation: String, workMode: String, userZip: String): String {
        val cleanLoc = if (jobLocation.isNotBlank()) jobLocation.replace("(Hybrid / Remote)", "").replace("(Remote)", "").trim() else "United States"
        val isRemoteJob = workMode.equals("Remote", ignoreCase = true) || jobLocation.contains("Remote", ignoreCase = true)

        val userCoords = resolveZipCoordinates(userZip)
        val jobCoords = resolveJobCoordinates(jobLocation)
        val distanceMiles = calculateDistanceMiles(userCoords, jobCoords)

        return if (isRemoteJob && !jobLocation.contains("New York", ignoreCase = true)) {
            if (cleanLoc.isBlank() || cleanLoc.equals("United States", ignoreCase = true)) {
                "Remote · Nationwide"
            } else {
                "$cleanLoc · Remote"
            }
        } else {
            val distLabel = if (distanceMiles <= 1) "1 mi away" else "$distanceMiles mi away"
            "$cleanLoc · $distLabel"
        }
    }

    /**
     * Evaluates whether a job passes the radius filter.
     * Supported search radii: "10 mi", "25 mi", "50 mi", "100 mi", "Remote".
     */
    fun matchesRadius(
        jobLocation: String,
        workMode: String,
        userZip: String,
        searchRadius: String,
    ): Boolean {
        val isRemoteJob = workMode.equals("Remote", ignoreCase = true) || jobLocation.contains("Remote", ignoreCase = true)

        if (searchRadius.equals("Remote", ignoreCase = true)) {
            return isRemoteJob
        }

        val radiusNumber = searchRadius.filter { it.isDigit() }.toIntOrNull() ?: 25
        val userCoords = resolveZipCoordinates(userZip)
        val jobCoords = resolveJobCoordinates(jobLocation)
        val distanceMiles = calculateDistanceMiles(userCoords, jobCoords)

        // For local/hybrid jobs, check exact distance against radius ceiling
        if (!isRemoteJob) {
            return distanceMiles <= radiusNumber
        }

        // For remote jobs, allow them unless the user explicitly configured a strictly local radius and mode
        return true
    }

    /**
     * Evaluates whether a job passes the work mode filter: "All", "Local", "Hybrid", "Remote".
     */
    fun matchesWorkMode(
        jobWorkMode: String,
        jobLocation: String,
        filterMode: String,
    ): Boolean {
        if (filterMode.isBlank() || filterMode.equals("All", ignoreCase = true)) return true

        val isRemote = jobWorkMode.equals("Remote", ignoreCase = true) || jobLocation.contains("Remote", ignoreCase = true)
        val isHybrid = jobWorkMode.equals("Hybrid", ignoreCase = true) || jobLocation.contains("Hybrid", ignoreCase = true)

        return when (filterMode.lowercase()) {
            "remote" -> isRemote
            "hybrid" -> isHybrid
            "local"  -> !isRemote && !isHybrid || jobWorkMode.equals("Local", ignoreCase = true)
            else     -> true
        }
    }
}
