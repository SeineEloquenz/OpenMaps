package nz.eloque.openmaps.core.model

/** Heights in meters along a route, the first at its start and each further one [spacingMeters] on. */
data class ElevationProfile(
    val spacingMeters: Double,
    val heights: List<Double>,
) {
    /** Meters climbed, from heights smoothed over a few samples so bridges and measurement noise do not count as hills. */
    val ascent: Double get() = climb(smoothed(heights))

    val descent: Double get() = climb(smoothed(heights).reversed())

    val lowest: Double? get() = heights.minOrNull()

    val highest: Double? get() = heights.maxOrNull()

    /** [other] continuing where this one ends. */
    operator fun plus(other: ElevationProfile) = ElevationProfile(spacingMeters, heights + other.heights.drop(1))
}

private fun climb(heights: List<Double>): Double {
    var total = 0.0
    var low = heights.firstOrNull() ?: return 0.0
    var high = low
    for (height in heights) {
        if (height > high) high = height
        if (height < high - NOISE_METERS) {
            if (high - low >= NOISE_METERS) total += high - low
            low = height
            high = height
        }
        if (height < low) {
            low = height
            high = height
        }
    }
    if (high - low >= NOISE_METERS) total += high - low
    return total
}

private const val NOISE_METERS = 3.0

private fun smoothed(heights: List<Double>): List<Double> =
    heights.indices.map { i ->
        val window = heights.subList(maxOf(0, i - SMOOTHING_RADIUS), minOf(heights.size, i + SMOOTHING_RADIUS + 1))
        window.average()
    }

private const val SMOOTHING_RADIUS = 2
