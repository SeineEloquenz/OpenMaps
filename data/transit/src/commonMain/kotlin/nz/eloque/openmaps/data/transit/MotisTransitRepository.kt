package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import nz.eloque.openmaps.core.model.DeparturesResult
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanResult
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.time.Instant

internal class MotisTransitRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : TransitRepository {
    override suspend fun stopsIn(bounds: GeoBounds): List<SearchResult.Stop> {
        val stops: List<PlaceDto> =
            client
                .get("$baseUrl/v6/map/stops") {
                    parameter("min", "${bounds.south},${bounds.east}")
                    parameter("max", "${bounds.north},${bounds.west}")
                    parameter("grouped", true)
                }.body()
        return stops.mapNotNull { stop ->
            val id = stop.stopId ?: return@mapNotNull null
            SearchResult.Stop(id, stop.name, LatLng(stop.lat, stop.lon), null, stop.modes.map(::transportMode).toSet())
        }
    }

    override suspend fun plan(request: PlanRequest): PlanResult {
        val response: PlanResponseDto =
            client
                .get("$baseUrl/v6/plan") {
                    parameter("fromPlace", request.from.asParameter())
                    parameter("toPlace", request.to.asParameter())
                    request.time?.let { parameter("time", it.toString()) }
                    parameter("arriveBy", request.arriveBy)
                    parameter("directModes", "WALK")
                    request.pageCursor?.let { parameter("pageCursor", it) }
                    routeOptions(request.options)
                }.body()
        return PlanResult(
            itineraries = response.itineraries.map { it.toItinerary() },
            direct = response.direct.map { it.toItinerary() },
            earlierCursor = response.previousPageCursor,
            laterCursor = response.nextPageCursor,
        )
    }

    override suspend fun trip(tripId: String): Itinerary {
        val response: ItineraryDto = client.get("$baseUrl/v6/trip") { parameter("tripId", tripId) }.body()
        return response.toItinerary()
    }

    override suspend fun departures(
        stopId: String,
        time: Instant?,
        count: Int,
        pageCursor: String?,
    ): DeparturesResult =
        stopTimes {
            parameter("stopId", stopId)
            parameter("withAlerts", true)
            time?.let { parameter("time", it.toString()) }
            parameter("n", count)
            pageCursor?.let { parameter("pageCursor", it) }
        }

    override suspend fun departuresNear(
        location: LatLng,
        radiusMeters: Int,
        count: Int,
    ): DeparturesResult =
        stopTimes {
            parameter("center", location.asParameter())
            parameter("radius", radiusMeters)
            parameter("n", count)
        }

    private suspend fun stopTimes(block: HttpRequestBuilder.() -> Unit): DeparturesResult {
        val response: StopTimesResponseDto = client.get("$baseUrl/v6/stoptimes", block).body()
        return DeparturesResult(
            departures = response.stopTimes.mapNotNull { it.toDeparture() },
            laterCursor = response.nextPageCursor,
        )
    }
}

internal fun LatLng.asParameter(): String = "$latitude,$longitude"

private fun PlanTarget.asParameter(): String =
    when (this) {
        is PlanTarget.Coordinates -> location.asParameter()
        is PlanTarget.Stop -> id
    }

private fun HttpRequestBuilder.routeOptions(options: RouteOptions) {
    if (options.modes.size < ModeGroup.entries.size) {
        val modes = options.modes.flatMap { it.modes }.map(::motisMode) + MINOR_MODES
        parameter("transitModes", modes.joinToString(","))
    }
    options.maxTransfers?.let { parameter("maxTransfers", it) }
    when (options.walkingSpeed) {
        WalkingSpeed.SLOW -> parameter("pedestrianSpeed", SLOW_WALK_METERS_PER_SECOND)
        WalkingSpeed.FAST -> parameter("pedestrianSpeed", FAST_WALK_METERS_PER_SECOND)
        WalkingSpeed.NORMAL -> Unit
    }
    if (options.wheelchair) {
        parameter("pedestrianProfile", "WHEELCHAIR")
        parameter("useRoutedTransfers", true)
    }
}

private fun motisMode(mode: TransportMode): String =
    when (mode) {
        TransportMode.LONG_DISTANCE_RAIL -> "LONG_DISTANCE"
        else -> mode.name
    }

/** Rare modes without a group of their own stay allowed when the user restricts the main ones. */
private val MINOR_MODES = listOf("FUNICULAR", "AERIAL_LIFT", "OTHER")
private const val SLOW_WALK_METERS_PER_SECOND = 0.9
private const val FAST_WALK_METERS_PER_SECOND = 1.6
