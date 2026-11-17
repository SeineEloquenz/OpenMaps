package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.StreetRequest
import nz.eloque.openmaps.core.model.StreetRouter
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.WalkingSpeed
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.core.model.decodePolyline
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
internal data class RouteRequestDto(
    val locations: List<LocationDto>,
    val costing: String,
    @SerialName("costing_options") val costingOptions: Map<String, CostingDto>,
    @SerialName("date_time") val dateTime: DateTimeDto,
    @SerialName("directions_type") val directionsType: String,
    val units: String,
)

@Serializable
internal data class LocationDto(
    val lat: Double,
    val lon: Double,
    @SerialName("search_filter") val searchFilter: SearchFilterDto,
    @SerialName("search_cutoff") val searchCutoff: Int,
)

/**
 * How far from a location Valhalla may look for a road, in meters. Valhalla's own 35 km would snap places outside a
 * server's area onto its border roads, giving a wrong route instead of none, and no fallback to the next server.
 */
internal const val SEARCH_CUTOFF_METERS = 1000

@Serializable
internal data class SearchFilterDto(
    @SerialName("exclude_closures") val excludeClosures: Boolean,
)

@Serializable
internal data class CostingDto(
    @SerialName("walking_speed") val walkingSpeed: Double? = null,
    val type: String? = null,
    @SerialName("use_tolls") val useTolls: Double? = null,
    @SerialName("use_highways") val useHighways: Double? = null,
    @SerialName("use_ferry") val useFerry: Double? = null,
)

@Serializable
internal data class DateTimeDto(
    val type: Int,
)

@Serializable
internal data class RouteResponseDto(
    val trip: TripDto,
)

@Serializable
internal data class TripDto(
    val legs: List<TripLegDto>,
)

@Serializable
internal data class TripLegDto(
    val shape: String,
    val summary: SummaryDto,
    val maneuvers: List<ManeuverDto> = emptyList(),
)

@Serializable
internal data class SummaryDto(
    val time: Double,
    val length: Double,
)

@Serializable
internal data class ManeuverDto(
    @SerialName("street_names") val streetNames: List<String>? = null,
    val length: Double = 0.0,
)

/** Street routes from Valhalla servers, which can also know about current road closures. Modes without a server are left out. */
internal class ValhallaStreetRouter(
    private val client: HttpClient,
    private val servers: ValhallaServers,
    private val now: () -> Instant = { Clock.System.now() },
) : StreetRouter {
    override suspend fun route(request: StreetRequest): List<Itinerary> =
        coroutineScope {
            request.modes
                .filter { servers.urls(it).isNotEmpty() }
                .mapNotNull { mode -> costing(mode)?.let { mode to it } }
                .map { (mode, costing) ->
                    async {
                        runCatching { servers.first(mode) { url -> route(url, request, mode, costing) } }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                    }
                }.awaitAll()
                .filterNotNull()
        }

    private suspend fun route(
        url: String,
        request: StreetRequest,
        mode: TransportMode,
        costing: String,
    ): Itinerary? {
        val body =
            RouteRequestDto(
                locations =
                    (listOf(request.from) + request.via + request.to).map {
                        LocationDto(it.latitude, it.longitude, SearchFilterDto(excludeClosures = false), SEARCH_CUTOFF_METERS)
                    },
                costing = costing,
                costingOptions = mapOf(costing to costingOptions(mode, request.options)),
                // Valhalla only applies live closures to routes leaving now.
                dateTime = DateTimeDto(type = 0),
                directionsType = "maneuvers",
                units = "kilometers",
            )
        val response: RouteResponseDto =
            try {
                client
                    .post("$url/route") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.body()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.BadRequest) return null
                throw e
            }
        return response.toItinerary(request, mode)
    }

    private fun RouteResponseDto.toItinerary(
        request: StreetRequest,
        mode: TransportMode,
    ): Itinerary? {
        val legs = trip.legs.takeIf { it.isNotEmpty() } ?: return null
        val duration = legs.sumOf { it.summary.time }.seconds
        val time = request.time ?: now()
        val start = if (request.arriveBy) time - duration else time
        val end = start + duration
        val leg =
            Leg.Street(
                mode = mode,
                from = waypoint(request.from, start),
                to = waypoint(request.to, end),
                start = EventTime(start, start, isRealTime = false),
                end = EventTime(end, end, isRealTime = false),
                geometry = legs.flatMap { decodePolyline(it.shape, precision = 6) },
                distanceMeters = legs.sumOf { it.summary.length } * 1000,
                streets = mainStreets(legs.flatMap { it.maneuvers }),
            )
        return Itinerary(
            id = "valhalla:${mode.name}:${start.epochSeconds}:${request.from}:${request.via.joinToString(":")}:${request.to}",
            start = start,
            end = end,
            transfers = 0,
            legs = listOf(leg),
        )
    }
}

internal fun costing(mode: TransportMode): String? =
    when (mode) {
        TransportMode.CAR -> "auto"
        TransportMode.BIKE -> "bicycle"
        TransportMode.WALK -> "pedestrian"
        else -> null
    }

internal fun costingOptions(
    mode: TransportMode,
    options: RouteOptions,
): CostingDto =
    if (mode == TransportMode.WALK) {
        CostingDto(
            walkingSpeed =
                when (options.walkingSpeed) {
                    WalkingSpeed.SLOW -> 4.0
                    WalkingSpeed.NORMAL -> 5.1
                    WalkingSpeed.FAST -> 6.0
                },
            type = if (options.wheelchair) "wheelchair" else null,
        )
    } else if (mode == TransportMode.CAR) {
        CostingDto(
            useTolls = AVOIDED.takeIf { options.avoidTolls },
            useHighways = AVOIDED.takeIf { options.avoidMotorways },
            useFerry = AVOIDED.takeIf { options.avoidFerries },
        )
    } else {
        CostingDto()
    }

// Unlike Valhalla's exclude options, 0 still allows these roads when there is no way around them.
private const val AVOIDED = 0.0

private fun waypoint(
    location: LatLng,
    time: Instant,
) = Waypoint(
    name = "",
    location = location,
    stopId = null,
    platform = null,
    scheduledPlatform = null,
    arrival = EventTime(time, time, isRealTime = false),
    departure = EventTime(time, time, isRealTime = false),
    isCancelled = false,
    timeZone = null,
)

/** The [count] streets with the longest stretches, in the order the route reaches them. */
internal fun mainStreets(
    maneuvers: List<ManeuverDto>,
    count: Int = 2,
): List<String> {
    val named = maneuvers.mapNotNull { m -> m.streetNames?.firstOrNull()?.let { it to m.length } }
    val longest =
        named
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, lengths) -> lengths.sum() }
            .entries
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }
            .toSet()
    return named.map { it.first }.distinct().filter { it in longest }
}
