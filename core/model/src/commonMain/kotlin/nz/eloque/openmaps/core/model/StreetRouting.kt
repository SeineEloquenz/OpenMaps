package nz.eloque.openmaps.core.model

import kotlin.time.Instant

data class StreetRequest(
    val from: LatLng,
    val to: LatLng,
    /** Of [TransportMode.CAR], [TransportMode.BIKE] and [TransportMode.WALK]. */
    val modes: Set<TransportMode>,
    /** Null for now. */
    val time: Instant?,
    val arriveBy: Boolean = false,
    val options: RouteOptions = RouteOptions(),
    /** Stops between [from] and [to], visited in order. */
    val via: List<LatLng> = emptyList(),
    /** Also asks for other ways to drive, which only routes without [via] stops can have. */
    val alternatives: Boolean = false,
)

/** Routes by car, bike or on foot alone, without public transport. */
interface StreetRouter {
    /** One route per requested mode, fastest first, with other car routes after it when [StreetRequest.alternatives] is set. Modes without a route are left out. */
    suspend fun route(request: StreetRequest): List<Itinerary>
}
