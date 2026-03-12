package com.luminai.travel

/**
 * LuminAIAssistant
 *
 * Offline, rule-based AI that interprets natural language location inputs
 * and resolves them to coordinates. No API key or cloud required.
 *
 * Strategy:
 *  1. Try to parse as raw coordinates  → instant, no network
 *  2. Check built-in landmark database → instant, no network
 *  3. Clean & normalize input          → strip filler phrases
 *  4. Forward geocode via Nominatim    → requires internet
 *
 * The AI also generates natural responses to display in the LUMIN AI panel.
 */
class LuminAIAssistant {

    // ─────────────────────────────────────────────────────────────────────────
    // Built-in landmark database (offline, instant resolution)
    // ─────────────────────────────────────────────────────────────────────────

    private val landmarks = mapOf(
        // Europe
        "eiffel tower"            to GeocodingResult(48.8584, 2.2945, "Eiffel Tower, Paris, France", ""),
        "eiffel"                  to GeocodingResult(48.8584, 2.2945, "Eiffel Tower, Paris, France", ""),
        "louvre"                  to GeocodingResult(48.8606, 2.3376, "Louvre Museum, Paris, France", ""),
        "notre dame"              to GeocodingResult(48.8530, 2.3499, "Notre-Dame Cathedral, Paris, France", ""),
        "colosseum"               to GeocodingResult(41.8902, 12.4922, "Colosseum, Rome, Italy", ""),
        "coliseum"                to GeocodingResult(41.8902, 12.4922, "Colosseum, Rome, Italy", ""),
        "big ben"                 to GeocodingResult(51.5007, -0.1246, "Big Ben, London, UK", ""),
        "tower of london"         to GeocodingResult(51.5081, -0.0759, "Tower of London, UK", ""),
        "buckingham palace"       to GeocodingResult(51.5014, -0.1419, "Buckingham Palace, London, UK", ""),
        "sagrada familia"         to GeocodingResult(41.4036, 2.1744, "Sagrada Família, Barcelona, Spain", ""),
        "acropolis"               to GeocodingResult(37.9715, 23.7267, "Acropolis, Athens, Greece", ""),
        "parthenon"               to GeocodingResult(37.9715, 23.7267, "Parthenon, Athens, Greece", ""),
        "avenida da liberdade"    to GeocodingResult(38.7196, -9.1453, "Avenida da Liberdade, Lisbon, Portugal", ""),
        "belem tower"             to GeocodingResult(38.6916, -9.2160, "Belém Tower, Lisbon, Portugal", ""),
        "torre de belem"          to GeocodingResult(38.6916, -9.2160, "Belém Tower, Lisbon, Portugal", ""),

        // Americas
        "times square"            to GeocodingResult(40.7580, -73.9855, "Times Square, New York, USA", ""),
        "statue of liberty"       to GeocodingResult(40.6892, -74.0445, "Statue of Liberty, New York, USA", ""),
        "central park"            to GeocodingResult(40.7851, -73.9683, "Central Park, New York, USA", ""),
        "empire state building"   to GeocodingResult(40.7484, -73.9857, "Empire State Building, New York, USA", ""),
        "golden gate"             to GeocodingResult(37.8199, -122.4783, "Golden Gate Bridge, San Francisco, USA", ""),
        "golden gate bridge"      to GeocodingResult(37.8199, -122.4783, "Golden Gate Bridge, San Francisco, USA", ""),
        "hollywood sign"          to GeocodingResult(34.1341, -118.3215, "Hollywood Sign, Los Angeles, USA", ""),
        "disney world"            to GeocodingResult(28.3852, -81.5639, "Walt Disney World, Orlando, USA", ""),
        "niagara falls"           to GeocodingResult(43.0962, -79.0377, "Niagara Falls, USA/Canada", ""),
        "christ the redeemer"     to GeocodingResult(-22.9519, -43.2105, "Christ the Redeemer, Rio de Janeiro, Brazil", ""),
        "machu picchu"            to GeocodingResult(-13.1631, -72.5450, "Machu Picchu, Peru", ""),

        // Asia & Pacific
        "mount fuji"              to GeocodingResult(35.3606, 138.7274, "Mount Fuji, Japan", ""),
        "fuji"                    to GeocodingResult(35.3606, 138.7274, "Mount Fuji, Japan", ""),
        "tokyo tower"             to GeocodingResult(35.6586, 139.7454, "Tokyo Tower, Japan", ""),
        "shibuya crossing"        to GeocodingResult(35.6595, 139.7006, "Shibuya Crossing, Tokyo, Japan", ""),
        "great wall"              to GeocodingResult(40.4319, 116.5704, "Great Wall of China, Beijing", ""),
        "great wall of china"     to GeocodingResult(40.4319, 116.5704, "Great Wall of China, Beijing", ""),
        "forbidden city"          to GeocodingResult(39.9163, 116.3972, "Forbidden City, Beijing, China", ""),
        "burj khalifa"            to GeocodingResult(25.1972, 55.2744, "Burj Khalifa, Dubai, UAE", ""),
        "taj mahal"               to GeocodingResult(27.1751, 78.0421, "Taj Mahal, Agra, India", ""),
        "angkor wat"              to GeocodingResult(13.4125, 103.8670, "Angkor Wat, Cambodia", ""),
        "opera house"             to GeocodingResult(-33.8568, 151.2153, "Sydney Opera House, Australia", ""),
        "sydney opera house"      to GeocodingResult(-33.8568, 151.2153, "Sydney Opera House, Australia", ""),

        // Africa & Middle East
        "pyramids"                to GeocodingResult(29.9792, 31.1342, "Great Pyramid of Giza, Egypt", ""),
        "great pyramid"           to GeocodingResult(29.9792, 31.1342, "Great Pyramid of Giza, Egypt", ""),
        "sphinx"                  to GeocodingResult(29.9753, 31.1376, "Great Sphinx, Giza, Egypt", ""),
        "mount kilimanjaro"       to GeocodingResult(-3.0674, 37.3556, "Mount Kilimanjaro, Tanzania", ""),
        "kilimanjaro"             to GeocodingResult(-3.0674, 37.3556, "Mount Kilimanjaro, Tanzania", ""),

        // Capital cities shorthand
        "london"    to GeocodingResult(51.5074, -0.1278, "London, United Kingdom", ""),
        "paris"     to GeocodingResult(48.8566, 2.3522, "Paris, France", ""),
        "new york"  to GeocodingResult(40.7128, -74.0060, "New York City, USA", ""),
        "nyc"       to GeocodingResult(40.7128, -74.0060, "New York City, USA", ""),
        "tokyo"     to GeocodingResult(35.6762, 139.6503, "Tokyo, Japan", ""),
        "lisbon"    to GeocodingResult(38.7169, -9.1399, "Lisbon, Portugal", ""),
        "lisboa"    to GeocodingResult(38.7169, -9.1399, "Lisbon, Portugal", ""),
        "rome"      to GeocodingResult(41.9028, 12.4964, "Rome, Italy", ""),
        "beijing"   to GeocodingResult(39.9042, 116.4074, "Beijing, China", ""),
        "dubai"     to GeocodingResult(25.2048, 55.2708, "Dubai, UAE", ""),
        "sydney"    to GeocodingResult(-33.8688, 151.2093, "Sydney, Australia", ""),
        "moscow"    to GeocodingResult(55.7558, 37.6173, "Moscow, Russia", ""),
        "berlin"    to GeocodingResult(52.5200, 13.4050, "Berlin, Germany", ""),
        "madrid"    to GeocodingResult(40.4168, -3.7038, "Madrid, Spain", ""),
        "barcelona" to GeocodingResult(41.3851, 2.1734, "Barcelona, Spain", ""),
        "amsterdam" to GeocodingResult(52.3676, 4.9041, "Amsterdam, Netherlands", ""),
        "vienna"    to GeocodingResult(48.2082, 16.3738, "Vienna, Austria", ""),
        "prague"    to GeocodingResult(50.0755, 14.4378, "Prague, Czech Republic", ""),
        "bangkok"   to GeocodingResult(13.7563, 100.5018, "Bangkok, Thailand", ""),
        "singapore" to GeocodingResult(1.3521, 103.8198, "Singapore", ""),
        "seoul"     to GeocodingResult(37.5665, 126.9780, "Seoul, South Korea", ""),
        "mumbai"    to GeocodingResult(19.0760, 72.8777, "Mumbai, India", ""),
        "cairo"     to GeocodingResult(30.0444, 31.2357, "Cairo, Egypt", ""),
        "toronto"   to GeocodingResult(43.6532, -79.3832, "Toronto, Canada", ""),
        "chicago"   to GeocodingResult(41.8781, -87.6298, "Chicago, USA", ""),
        "miami"     to GeocodingResult(25.7617, -80.1918, "Miami, USA", ""),
        "los angeles" to GeocodingResult(34.0522, -118.2437, "Los Angeles, USA", ""),
        "la"          to GeocodingResult(34.0522, -118.2437, "Los Angeles, USA", ""),
        "san francisco" to GeocodingResult(37.7749, -122.4194, "San Francisco, USA", ""),
        "sf"            to GeocodingResult(37.7749, -122.4194, "San Francisco, USA", ""),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Filler phrases to strip from input
    // ─────────────────────────────────────────────────────────────────────────

    private val fillerPhrases = listOf(
        "take me to", "teleport to", "go to", "travel to", "navigate to",
        "set location to", "set gps to", "move to", "show me", "find",
        "where is", "i want to go to", "i'm going to", "i am going to",
        "transport me to", "beam me to", "fly me to"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Main interpret function
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Interpret a user's location input and return a [GeocodingResult].
     *
     * @param input  Raw user text
     * @param geo    [GeocodingService] for Nominatim fallback
     * @return       [GeocodingResult] or null if not resolvable
     */
    suspend fun interpretLocation(input: String, geo: GeocodingService): GeocodingResult? {
        val cleaned = cleanInput(input)

        // 1. Try raw coordinate parsing first (fast, offline)
        geo.parseCoordinates(cleaned)?.let { return it }

        // 2. Check local landmark database (fast, offline)
        lookupLandmark(cleaned)?.let { return it }

        // 3. Fall back to Nominatim geocoding (online)
        return geo.geocode(cleaned)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Strip filler phrases and normalise whitespace.
     */
    private fun cleanInput(input: String): String {
        var result = input.lowercase().trim()
        fillerPhrases.forEach { phrase ->
            result = result.removePrefix(phrase).trim()
        }
        return result.trim()
    }

    /**
     * Look up the cleaned query against the local landmark map.
     * Tries exact match first, then partial match.
     */
    private fun lookupLandmark(query: String): GeocodingResult? {
        // Exact
        landmarks[query]?.let { return it }

        // Prefix match (e.g. "eiffel tower paris" → "eiffel tower")
        for ((key, value) in landmarks) {
            if (query.startsWith(key) || key.startsWith(query)) {
                return value
            }
        }

        // Contains match
        for ((key, value) in landmarks) {
            if (query.contains(key) || key.contains(query)) {
                return value
            }
        }

        return null
    }
}
