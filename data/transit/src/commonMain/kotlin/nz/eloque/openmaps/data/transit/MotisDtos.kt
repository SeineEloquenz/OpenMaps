package nz.eloque.openmaps.data.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MOTIS writes some integers in exponent form (`2E0`), so every number is read as Double.

@Serializable
internal data class MatchDto(
    val type: String,
    val name: String,
    val id: String = "",
    val lat: Double,
    val lon: Double,
    val category: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zip: String? = null,
    val areas: List<AreaDto> = emptyList(),
    val modes: List<String> = emptyList(),
)

@Serializable
internal data class AreaDto(
    val name: String,
    val adminLevel: Double = 0.0,
    @SerialName("default") val isDefault: Boolean = false,
)

@Serializable
internal data class PlaceDto(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val tz: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val cancelled: Boolean = false,
    val alerts: List<AlertDto> = emptyList(),
    val modes: List<String> = emptyList(),
)

@Serializable
internal data class AlertDto(
    val headerText: String = "",
    val descriptionText: String? = null,
)

@Serializable
internal data class PolylineDto(
    val points: String,
    val precision: Double = 6.0,
)

@Serializable
internal data class LegDto(
    val mode: String,
    val from: PlaceDto,
    val to: PlaceDto,
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val realTime: Boolean = false,
    val legGeometry: PolylineDto,
    val distance: Double? = null,
    val headsign: String? = null,
    val displayName: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val tripId: String? = null,
    val intermediateStops: List<PlaceDto> = emptyList(),
    val alerts: List<AlertDto> = emptyList(),
    val cancelled: Boolean = false,
    val wheelchairAccessible: String? = null,
)

@Serializable
internal data class ItineraryDto(
    val id: String = "",
    val startTime: String,
    val endTime: String,
    val transfers: Double = 0.0,
    val legs: List<LegDto>,
)

@Serializable
internal data class PlanResponseDto(
    val itineraries: List<ItineraryDto> = emptyList(),
    val direct: List<ItineraryDto> = emptyList(),
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

@Serializable
internal data class StopTimeDto(
    val place: PlaceDto,
    val mode: String,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val displayName: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val tripId: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
)

@Serializable
internal data class StopTimesResponseDto(
    val stopTimes: List<StopTimeDto> = emptyList(),
    val nextPageCursor: String? = null,
)
