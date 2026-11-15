package nz.eloque.openmaps.core.model

import kotlin.time.Instant

interface Geocoder {
    suspend fun search(
        query: String,
        near: LatLng?,
        stopsFirst: Boolean = false,
    ): List<SearchResult>

    /** The address nearest to [location]. */
    suspend fun reverse(location: LatLng): SearchResult?
}

interface PlaceDetailsSource {
    suspend fun details(osmId: OsmId): PlaceDetails?

    /** Details of many places in as few requests as the service allows. Places it does not know are left out. */
    suspend fun detailsOf(osmIds: List<OsmId>): Map<OsmId, PlaceDetails>
}

sealed interface PlanTarget {
    data class Coordinates(
        val location: LatLng,
    ) : PlanTarget

    data class Stop(
        val id: String,
    ) : PlanTarget
}

data class PlanRequest(
    val from: PlanTarget,
    val to: PlanTarget,
    val time: Instant?,
    val arriveBy: Boolean = false,
    val pageCursor: String? = null,
    val options: RouteOptions = RouteOptions(),
    /** Modes of routes that use no public transport. */
    val directModes: Set<TransportMode> = setOf(TransportMode.WALK),
    /** False to ask for direct routes only, skipping the public transport search. */
    val withTransit: Boolean = true,
)

data class PlanResult(
    val itineraries: List<Itinerary>,
    val direct: List<Itinerary>,
    val earlierCursor: String?,
    val laterCursor: String?,
)

data class DeparturesResult(
    val departures: List<Departure>,
    val laterCursor: String?,
)

interface TransitRepository {
    suspend fun stopsIn(bounds: GeoBounds): List<SearchResult.Stop>

    suspend fun plan(request: PlanRequest): PlanResult

    /** The whole run of one vehicle, as an itinerary with a single transit leg. */
    suspend fun trip(tripId: String): Itinerary

    suspend fun departures(
        stopId: String,
        time: Instant? = null,
        count: Int = 30,
        pageCursor: String? = null,
    ): DeparturesResult

    suspend fun departuresNear(
        location: LatLng,
        radiusMeters: Int,
        count: Int = 30,
    ): DeparturesResult
}
