package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LiveVehicles
import nz.eloque.openmaps.core.model.VehicleRun
import nz.eloque.openmaps.core.model.decodePolyline
import kotlin.time.Instant

@Serializable
internal data class TripSegmentDto(
    val trips: List<TripRefDto> = emptyList(),
    val routeColor: String? = null,
    val mode: String,
    val to: PlaceDto,
    val departure: String,
    val arrival: String,
    val realTime: Boolean = false,
    val polyline: String,
)

@Serializable
internal data class TripRefDto(
    val tripId: String,
    val displayName: String? = null,
    val routeShortName: String? = null,
)

/** Vehicles moving between stops, as a MOTIS server places them from timetables and live delays. */
internal class MotisLiveVehicles(
    private val client: HttpClient,
    private val baseUrl: () -> String,
) : LiveVehicles {
    override suspend fun runs(
        bounds: GeoBounds,
        zoom: Int,
        from: Instant,
        until: Instant,
    ): List<VehicleRun> {
        val segments: List<TripSegmentDto> =
            client
                .get("${baseUrl()}/v6/map/trips") {
                    parameter("min", "${bounds.south},${bounds.west}")
                    parameter("max", "${bounds.north},${bounds.east}")
                    parameter("zoom", zoom)
                    parameter("startTime", Instant.fromEpochSeconds(from.epochSeconds))
                    parameter("endTime", Instant.fromEpochSeconds(until.epochSeconds))
                }.body()
        return segments.mapNotNull { it.toRun() }
    }
}

internal fun TripSegmentDto.toRun(): VehicleRun? {
    val trip = trips.firstOrNull() ?: return null
    return VehicleRun(
        tripId = trip.tripId,
        name = (trip.displayName ?: trip.routeShortName).orEmpty(),
        mode = transportMode(mode),
        color = parseColor(routeColor),
        nextStop = to.name,
        departure = Instant.parse(departure),
        arrival = Instant.parse(arrival),
        path = decodePolyline(polyline, POLYLINE_PRECISION),
        realTime = realTime,
    )
}

private const val POLYLINE_PRECISION = 5
