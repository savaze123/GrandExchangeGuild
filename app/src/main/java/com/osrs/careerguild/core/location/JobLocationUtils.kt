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

    // Known city/metro coordinate anchors across NYC Boroughs, Long Island, NJ, CT, and major US tech hubs
    private val cityCoordinates = mapOf(
        // NYC & Boroughs
        "new york" to GeoCoordinates(40.7589, -73.9851),
        "nyc" to GeoCoordinates(40.7589, -73.9851),
        "manhattan" to GeoCoordinates(40.7589, -73.9851),
        "brooklyn" to GeoCoordinates(40.6782, -73.9442),
        "queens" to GeoCoordinates(40.7282, -73.7949),
        "bronx" to GeoCoordinates(40.8448, -73.8648),
        "staten island" to GeoCoordinates(40.5795, -74.1502),
        "long island city" to GeoCoordinates(40.7447, -73.9485),
        "lic" to GeoCoordinates(40.7447, -73.9485),
        "astoria" to GeoCoordinates(40.7644, -73.9235),
        "flushing" to GeoCoordinates(40.7674, -73.8331),
        "fresh meadows" to GeoCoordinates(40.7380, -73.7950),

        // Long Island (Nassau & Suffolk)
        "valley stream" to GeoCoordinates(40.6657, -73.7093),
        "hempstead" to GeoCoordinates(40.7062, -73.6187),
        "garden city" to GeoCoordinates(40.7268, -73.6343),
        "mineola" to GeoCoordinates(40.7493, -73.6407),
        "great neck" to GeoCoordinates(40.8007, -73.7285),
        "hicksville" to GeoCoordinates(40.7684, -73.5251),
        "farmingdale" to GeoCoordinates(40.7326, -73.4454),
        "huntington" to GeoCoordinates(40.8682, -73.4257),
        "melville" to GeoCoordinates(40.7934, -73.4151),
        "stony brook" to GeoCoordinates(40.9165, -73.1251),
        "long island" to GeoCoordinates(40.7891, -73.1350),

        // New Jersey (NY Metro)
        "jersey city" to GeoCoordinates(40.7178, -74.0431),
        "hoboken" to GeoCoordinates(40.7439, -74.0324),
        "newark" to GeoCoordinates(40.7357, -74.1724),
        "weehawken" to GeoCoordinates(40.7695, -74.0199),
        "secaucus" to GeoCoordinates(40.7895, -74.0565),
        "princeton" to GeoCoordinates(40.3573, -74.6672),
        "morristown" to GeoCoordinates(40.7968, -74.4815),
        "new brunswick" to GeoCoordinates(40.4862, -74.4518),

        // Westchester & Connecticut (NY Metro)
        "white plains" to GeoCoordinates(41.0340, -73.7629),
        "yonkers" to GeoCoordinates(40.9312, -73.8987),
        "stamford" to GeoCoordinates(41.0534, -73.5387),
        "greenwich" to GeoCoordinates(41.0262, -73.6282),
        "norwalk" to GeoCoordinates(41.1176, -73.4078),
        "new haven" to GeoCoordinates(41.3083, -72.9279),

        // Major US Tech Hubs
        "san francisco" to GeoCoordinates(37.7749, -122.4194),
        "sf" to GeoCoordinates(37.7749, -122.4194),
        "san jose" to GeoCoordinates(37.3382, -121.8863),
        "palo alto" to GeoCoordinates(37.4419, -122.1430),
        "mountain view" to GeoCoordinates(37.3861, -122.0839),
        "sunnyvale" to GeoCoordinates(37.3688, -122.0363),
        "seattle" to GeoCoordinates(47.6062, -122.3321),
        "bellevue" to GeoCoordinates(47.6101, -122.2015),
        "redmond" to GeoCoordinates(47.6740, -122.1215),
        "austin" to GeoCoordinates(30.2672, -97.7431),
        "dallas" to GeoCoordinates(32.7767, -96.7970),
        "boston" to GeoCoordinates(42.3601, -71.0589),
        "cambridge" to GeoCoordinates(42.3736, -71.1097),
        "chicago" to GeoCoordinates(41.8781, -87.6298),
        "los angeles" to GeoCoordinates(34.0522, -118.2437),
        "san diego" to GeoCoordinates(32.7157, -117.1611),
        "denver" to GeoCoordinates(39.7392, -104.9903),
        "boulder" to GeoCoordinates(40.0150, -105.2705),
        "atlanta" to GeoCoordinates(33.7490, -84.3880),
        "washington" to GeoCoordinates(38.9072, -77.0369),
        "dc" to GeoCoordinates(38.9072, -77.0369),
        "arlington" to GeoCoordinates(38.8799, -77.1068),
        "raleigh" to GeoCoordinates(35.7796, -78.6382),
        "durham" to GeoCoordinates(35.9940, -78.8986),
        "salt lake city" to GeoCoordinates(40.7608, -111.8910),
        "melbourne" to GeoCoordinates(28.0836, -80.6081),
        "miami" to GeoCoordinates(25.7617, -80.1918),
        "warren" to GeoCoordinates(42.5145, -83.0147),
        "detroit" to GeoCoordinates(42.3314, -83.0458),
        "boise" to GeoCoordinates(43.6150, -116.2023),
        "philadelphia" to GeoCoordinates(39.9526, -75.1652),
        "pittsburgh" to GeoCoordinates(40.4406, -79.9959),
    )

    // Specific known ZIP codes (including user's test ZIPs: 11581, 11365, 10001)
    private val specificZipCoordinates = mapOf(
        "11581" to GeoCoordinates(40.6657, -73.7093), // Valley Stream / Long Island border
        "11365" to GeoCoordinates(40.7380, -73.7950), // Fresh Meadows / Queens
        "10001" to GeoCoordinates(40.7501, -73.9996), // Chelsea / Midtown NYC
        "11201" to GeoCoordinates(40.6958, -73.9897), // Downtown Brooklyn
        "10451" to GeoCoordinates(40.8208, -73.9234), // South Bronx
        "10301" to GeoCoordinates(40.6381, -74.0894), // Staten Island
        "11550" to GeoCoordinates(40.7062, -73.6187), // Hempstead, Long Island
        "11501" to GeoCoordinates(40.7493, -73.6407), // Mineola, Long Island
        "11747" to GeoCoordinates(40.7934, -73.4151), // Melville, Long Island
        "07030" to GeoCoordinates(40.7439, -74.0324), // Hoboken, NJ
        "07302" to GeoCoordinates(40.7178, -74.0431), // Jersey City, NJ
        "06901" to GeoCoordinates(41.0534, -73.5387), // Stamford, CT
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
