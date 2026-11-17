package nz.eloque.openmaps.core.model

import kotlin.time.Instant

/** CAP severity levels, mildest first. */
enum class WarningSeverity { UNKNOWN, MINOR, MODERATE, SEVERE, EXTREME }

/** An official weather warning, in the language its agency issued it in. */
data class WeatherWarning(
    val id: String,
    val title: String,
    val severity: WarningSeverity,
    val start: Instant?,
    val end: Instant?,
    val description: String?,
    val regions: List<String>,
    /** Where the warning was published, such as `meteoalarm.org`. */
    val source: String?,
)

interface WeatherWarnings {
    /** Warnings in force at [location] now or later, the most severe first. */
    suspend fun at(location: LatLng): List<WeatherWarning>
}
