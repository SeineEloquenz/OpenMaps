package nz.eloque.openmaps.core.model

import kotlin.time.Instant

interface Geocoder {
    suspend fun search(
        query: String,
        near: LatLng?,
        stopsFirst: Boolean = false,
    ): List<SearchResult>

    suspend fun reverse(location: LatLng): SearchResult?
}

interface PlaceDetailsSource {
    suspend fun details(osmId: OsmId): PlaceDetails?
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
    suspend fun plan(request: PlanRequest): PlanResult

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
