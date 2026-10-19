package com.osrs.careerguild.data.remote.scraper

/**
 * ZIP-radius geo-filter for TECH_RADAR_SCRAPER.
 *
 * Phase 2: Keyword-based location matching against a configured set of
 * preferred states/cities stored in DataStore.
 *
 * Phase 3 (TODO): Replace with real geocoding using a ZIP-to-lat/lon lookup
 * and a Haversine-formula radius check. Candidates: Zippopotam.us API (free)
 * or a bundled ZIP code SQLite table.
 *
 * @property allowedKeywords  Case-insensitive substrings matched against
 *                            a listing's location strings. Also accepts
 *                            "Remote" as a universal pass-through.
 */
class GeoFilter(
    private val allowedKeywords: Set<String> = DEFAULT_LOCATIONS,
) {

    /**
     * Returns true if [locations] contains at least one entry matching any of
     * the [allowedKeywords] or the word "remote" (case-insensitive).
     *
     * An empty [allowedKeywords] set passes ALL locations (no filter).
     */
    fun passes(locations: List<String>?): Boolean {
        if (locations.isNullOrEmpty()) return true  // no location data = don't filter
        if (allowedKeywords.isEmpty())  return true  // filter disabled

        return locations.any { loc ->
            val lower = loc.lowercase()
            lower.contains("remote") || allowedKeywords.any { kw -> lower.contains(kw.lowercase()) }
        }
    }

    companion object {
        /** Sensible US tech-hub defaults — user can override via DataStore. */
        val DEFAULT_LOCATIONS: Set<String> = setOf(
            "Remote",
            "New York",
            "San Francisco",
            "Seattle",
            "Austin",
            "Boston",
            "Chicago",
            "Los Angeles",
            "Denver",
            "Atlanta",
        )
    }
}
