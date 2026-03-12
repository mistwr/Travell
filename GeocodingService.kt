package com.luminai.travel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GeocodingService
 *
 * Provides forward geocoding (address → coordinates) and reverse geocoding
 * (coordinates → address) using the OpenStreetMap Nominatim API.
 *
 * Nominatim usage policy: include a User-Agent identifying the app,
 * and limit requests (not more than 1/sec). We respect this.
 *
 * API docs: https://nominatim.org/release-docs/latest/api/Search/
 */
class GeocodingService {

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org"
        private const val USER_AGENT = "LUMINAI-Travel/1.0 (com.luminai.travel)"
        private const val TIMEOUT_SECONDS = 10L
    }

    // Shared OkHttp client with sensible timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Forward geocode: converts a text query to [GeocodingResult].
     * Runs on [Dispatchers.IO].
     *
     * @param query Human-readable address or place name
     * @return [GeocodingResult] or null if not found
     */
    suspend fun geocode(query: String): GeocodingResult? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search?q=$encodedQuery&format=json&limit=1&addressdetails=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONArray(body)

            if (json.length() == 0) return@withContext null

            val first = json.getJSONObject(0)
            GeocodingResult(
                latitude = first.getDouble("lat"),
                longitude = first.getDouble("lon"),
                displayName = first.getString("display_name").let { cleanDisplayName(it) },
                rawDisplayName = first.getString("display_name")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse raw coordinate input in format "lat, lon" or "lat lon".
     *
     * @param input Raw text that may contain coordinates
     * @return [GeocodingResult] or null if not valid coordinates
     */
    fun parseCoordinates(input: String): GeocodingResult? {
        // Patterns: "48.8566, 2.3522" | "48.8566 2.3522" | "48°51'N 2°21'E"
        val cleanInput = input.trim()

        // Try decimal pattern: optional negative, digits, dot, digits
        val decimalPattern = Regex(
            """^(-?\d{1,3}(?:\.\d+)?)[,\s]+(-?\d{1,3}(?:\.\d+)?)$"""
        )

        val match = decimalPattern.find(cleanInput)
        if (match != null) {
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return null

            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null

            return GeocodingResult(
                latitude = lat,
                longitude = lon,
                displayName = "%.6f, %.6f".format(lat, lon),
                rawDisplayName = "%.6f, %.6f".format(lat, lon)
            )
        }

        return null
    }

    /**
     * Reverse geocode: converts coordinates to a human-readable address.
     *
     * @return Short display name or null
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/reverse?lat=$lat&lon=$lon&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            json.optString("display_name").let { cleanDisplayName(it) }
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shorten Nominatim's verbose display name to a more readable form.
     * e.g. "Eiffel Tower, 5, Avenue Anatole France, ..." → "Eiffel Tower, Paris, France"
     */
    private fun cleanDisplayName(name: String): String {
        val parts = name.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            parts.size >= 4 -> "${parts[0]}, ${parts[parts.size - 3]}, ${parts.last()}"
            parts.size >= 2 -> "${parts[0]}, ${parts.last()}"
            else -> name
        }
    }
}

/**
 * Result from geocoding a location.
 */
data class GeocodingResult(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val rawDisplayName: String
)
