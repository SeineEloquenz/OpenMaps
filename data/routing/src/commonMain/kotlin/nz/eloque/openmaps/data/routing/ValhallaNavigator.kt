package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Announcement
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LocationFix
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.ManeuverType
import nz.eloque.openmaps.core.model.NavigationInstruction
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.decodePolyline
import org.maplibre.navigation.core.location.Location
import org.maplibre.navigation.core.location.engine.LocationEngine
import org.maplibre.navigation.core.location.replay.ReplayRouteLocationEngine
import org.maplibre.navigation.core.milestone.VoiceInstructionMilestone
import org.maplibre.navigation.core.models.BannerText
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.models.DirectionsRoute
import org.maplibre.navigation.core.models.LegStep
import org.maplibre.navigation.core.models.ManeuverModifier
import org.maplibre.navigation.core.models.StepManeuver
import org.maplibre.navigation.core.navigation.MapLibreNavigation
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions
import org.maplibre.navigation.core.routeprogress.RouteProgress
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import org.maplibre.navigation.core.models.RouteOptions as NavigationRouteOptions

@Serializable
internal data class NavigationRequestDto(
    val locations: List<NavigationLocationDto>,
    val costing: String,
    @SerialName("costing_options") val costingOptions: Map<String, CostingDto>,
    @SerialName("date_time") val dateTime: DateTimeDto,
    val format: String,
    @SerialName("shape_format") val shapeFormat: String,
    @SerialName("banner_instructions") val bannerInstructions: Boolean,
    @SerialName("voice_instructions") val voiceInstructions: Boolean,
    val language: String?,
    val units: String,
)

@Serializable
internal data class NavigationLocationDto(
    val lat: Double,
    val lon: Double,
    @SerialName("search_filter") val searchFilter: SearchFilterDto,
    val heading: Int? = null,
    @SerialName("heading_tolerance") val headingTolerance: Int? = null,
)

/** Routes with banner and voice instructions in the Mapbox Directions format MapLibre Navigation reads. */
internal class ValhallaNavigationRoutes(
    private val client: HttpClient,
    private val baseUrl: () -> String?,
    private val language: () -> String?,
    private val units: () -> DistanceUnit,
) {
    val isConfigured: Boolean
        get() = baseUrl() != null

    /** Null when Valhalla finds no route. [heading] keeps a rerouted trip going the way the user faces. */
    suspend fun route(
        from: LatLng,
        heading: Float?,
        to: LatLng,
        mode: TransportMode,
        options: RouteOptions,
    ): DirectionsRoute? {
        val url = checkNotNull(baseUrl()) { "No Valhalla server set" }.trimEnd('/')
        val costing = checkNotNull(costing(mode)) { "No street routing for $mode" }
        val start =
            NavigationLocationDto(
                from.latitude,
                from.longitude,
                SearchFilterDto(excludeClosures = false),
                heading = heading?.toInt(),
                headingTolerance = heading?.let { HEADING_TOLERANCE },
            )
        val body =
            NavigationRequestDto(
                locations = listOf(start, NavigationLocationDto(to.latitude, to.longitude, SearchFilterDto(excludeClosures = false))),
                costing = costing,
                costingOptions = mapOf(costing to costingOptions(mode, options)),
                // Valhalla only applies live closures to routes leaving now.
                dateTime = DateTimeDto(type = 0),
                format = "osrm",
                shapeFormat = "polyline6",
                bannerInstructions = true,
                voiceInstructions = true,
                language = language(),
                units = if (units() == DistanceUnit.IMPERIAL) "miles" else "kilometers",
            )
        val text =
            try {
                client
                    .post("$url/route") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.bodyAsText()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.BadRequest) return null
                throw e
            }
        val language = body.language
        return DirectionsResponse.fromJson(text).routes.firstOrNull()?.let { route ->
            route.copy(
                routeOptions =
                    NavigationRouteOptions(
                        baseUrl = url,
                        user = "valhalla",
                        profile = costing,
                        coordinates = listOf(from, to).map { Position(longitude = it.longitude, latitude = it.latitude) },
                        language = language,
                        geometries = "polyline6",
                        steps = true,
                        voiceInstructions = true,
                        bannerInstructions = true,
                    ),
            )
        }
    }
}

/** Guidance by MapLibre Navigation along Valhalla routes, rerouting through Valhalla when the user leaves the route. */
internal class ValhallaNavigator(
    private val routes: ValhallaNavigationRoutes,
    private val location: LocationUpdates,
) : Navigator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<NavigationState?>(null)
    override val state: StateFlow<NavigationState?> = _state.asStateFlow()
    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    override val announcements: Flow<Announcement> = _announcements.asSharedFlow()
    override val isAvailable: Boolean
        get() = routes.isConfigured

    private var trip: Trip? = null

    private class Trip(
        val navigation: MapLibreNavigation,
        val to: LatLng,
        val mode: TransportMode,
        val options: RouteOptions,
        val replay: ReplayRouteLocationEngine?,
        var geometry: List<LatLng>,
        var voiceLanguage: String?,
        var rerouting: Boolean = false,
        var lastReroute: Long = 0,
    )

    override suspend fun start(
        from: LatLng,
        to: LatLng,
        mode: TransportMode,
        options: RouteOptions,
        simulate: Boolean,
    ) {
        val route = routes.route(from, null, to, mode, options) ?: error("No route to the destination")
        stop()
        val replay = if (simulate) ReplayRouteLocationEngine(scope).apply { assign(route) } else null
        val navigation = MapLibreNavigation(navigationOptions(mode), replay ?: FixLocationEngine(location))
        val trip = Trip(navigation, to, mode, options, replay, route.points(), route.voiceLanguage)
        this.trip = trip
        navigation.addProgressChangeListener { location, progress -> onProgress(trip, location, progress) }
        navigation.addMilestoneEventListener { _, instruction, milestone ->
            if (milestone is VoiceInstructionMilestone && !instruction.isNullOrBlank()) {
                _announcements.tryEmit(Announcement(instruction, trip.voiceLanguage))
            }
        }
        navigation.addOffRouteListener { location -> reroute(trip, location) }
        _state.value = initialState(trip, route)
        navigation.startNavigation(route)
    }

    override fun stop() {
        trip?.let {
            it.navigation.stopNavigation()
            it.navigation.onDestroy()
            it.replay?.onStop()
        }
        trip = null
        _state.value = null
    }

    private fun onProgress(
        trip: Trip,
        location: Location,
        progress: RouteProgress,
    ) {
        if (this.trip !== trip) return
        val steps = progress.currentLeg.steps
        val step = steps.getOrNull(progress.stepIndex)
        val next = steps.getOrNull(progress.stepIndex + 1)
        val arrived =
            progress.distanceRemaining < ARRIVAL_METERS ||
                (next?.maneuver?.type == StepManeuver.Type.ARRIVE && progress.stepDistanceRemaining < ARRIVAL_METERS)
        _state.value =
            NavigationState(
                mode = trip.mode,
                route = trip.geometry,
                position = LatLng(location.latitude, location.longitude),
                bearing = location.bearing?.toDouble(),
                travelledMeters = progress.distanceTraveled,
                instruction = step?.upcoming(progress.stepDistanceRemaining, next),
                metersToManeuver = progress.stepDistanceRemaining,
                then = next?.upcoming(next.distance, steps.getOrNull(progress.stepIndex + 2)),
                remainingMeters = progress.distanceRemaining,
                remainingTime = progress.durationRemaining.seconds,
                status =
                    when {
                        arrived -> NavigationStatus.ARRIVED
                        trip.rerouting -> NavigationStatus.REROUTING
                        else -> NavigationStatus.NAVIGATING
                    },
            )
    }

    private fun reroute(
        trip: Trip,
        location: Location,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (trip.rerouting || trip.replay != null || now - trip.lastReroute < REROUTE_PAUSE_MILLIS) return
        trip.rerouting = true
        trip.lastReroute = now
        _state.update { it?.copy(status = NavigationStatus.REROUTING) }
        scope.launch {
            val route =
                runCatching {
                    routes.route(LatLng(location.latitude, location.longitude), location.bearing, trip.to, trip.mode, trip.options)
                }.getOrNull()
            trip.rerouting = false
            if (this@ValhallaNavigator.trip !== trip) return@launch
            if (route == null) {
                _state.update { it?.copy(status = NavigationStatus.OFF_ROUTE) }
                return@launch
            }
            trip.geometry = route.points()
            trip.voiceLanguage = route.voiceLanguage ?: trip.voiceLanguage
            trip.navigation.startNavigation(route)
        }
    }

    private fun initialState(
        trip: Trip,
        route: DirectionsRoute,
    ): NavigationState {
        val steps = route.legs.firstOrNull()?.steps.orEmpty()
        val first = steps.firstOrNull()
        return NavigationState(
            mode = trip.mode,
            route = trip.geometry,
            position = null,
            bearing = null,
            travelledMeters = 0.0,
            instruction = first?.upcoming(first.distance, steps.getOrNull(1)),
            metersToManeuver = first?.distance ?: 0.0,
            then = steps.getOrNull(1)?.let { it.upcoming(it.distance, steps.getOrNull(2)) },
            remainingMeters = route.distance,
            remainingTime = route.duration.seconds,
            status = NavigationStatus.NAVIGATING,
        )
    }
}

/** Feeds MapLibre Navigation from the app's own location provider. */
private class FixLocationEngine(
    private val updates: LocationUpdates,
) : LocationEngine {
    private var last: Location? = null

    override fun listenToLocation(request: LocationEngine.Request): Flow<Location> =
        updates
            .updates()
            .map { it.toLocation() }
            .onEach { last = it }

    override suspend fun getLastLocation(): Location? = last
}

private fun LocationFix.toLocation() =
    Location(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = accuracyMeters,
        speedMetersPerSeconds = speed,
        bearing = bearing,
        timeMilliseconds = timeMillis,
    )

private fun DirectionsRoute.points(): List<LatLng> = decodePolyline(geometry, precision = 6)

/** The maneuver at the end of this step, from its banner closest ahead of the user. */
internal fun LegStep.upcoming(
    metersLeft: Double,
    next: LegStep?,
): NavigationInstruction? {
    val banner =
        bannerInstructions.orEmpty().sortedBy { it.distanceAlongGeometry }.firstOrNull { it.distanceAlongGeometry >= metersLeft - 1 }
            ?: bannerInstructions?.lastOrNull()
    val primary = banner?.primary
    if (primary != null) {
        return NavigationInstruction(
            text = primary.text,
            secondary = banner.secondary?.text?.takeIf { it.isNotBlank() },
            maneuver = primary.maneuverType(),
            exit = next?.maneuver?.exit,
        )
    }
    val maneuver = next?.maneuver ?: return null
    return NavigationInstruction(maneuver.instruction.orEmpty(), null, maneuverType(maneuver.type, maneuver.modifier), maneuver.exit)
}

private fun BannerText.maneuverType() = maneuverType(type, modifier)

internal fun maneuverType(
    type: StepManeuver.Type?,
    modifier: ManeuverModifier.Type?,
): ManeuverType =
    when (type) {
        StepManeuver.Type.ARRIVE -> ManeuverType.ARRIVE
        StepManeuver.Type.DEPART -> ManeuverType.DEPART
        StepManeuver.Type.ROUNDABOUT,
        StepManeuver.Type.ROTARY,
        StepManeuver.Type.ROUNDABOUT_TURN,
        StepManeuver.Type.EXIT_ROUNDABOUT,
        StepManeuver.Type.EXIT_ROTARY,
        -> ManeuverType.ROUNDABOUT
        else ->
            when (modifier) {
                ManeuverModifier.Type.UTURN -> ManeuverType.U_TURN
                ManeuverModifier.Type.SHARP_RIGHT -> ManeuverType.SHARP_RIGHT
                ManeuverModifier.Type.RIGHT -> ManeuverType.RIGHT
                ManeuverModifier.Type.SLIGHT_RIGHT -> ManeuverType.SLIGHT_RIGHT
                ManeuverModifier.Type.STRAIGHT -> ManeuverType.STRAIGHT
                ManeuverModifier.Type.SLIGHT_LEFT -> ManeuverType.SLIGHT_LEFT
                ManeuverModifier.Type.LEFT -> ManeuverType.LEFT
                ManeuverModifier.Type.SHARP_LEFT -> ManeuverType.SHARP_LEFT
                null -> ManeuverType.OTHER
            }
    }

private fun navigationOptions(mode: TransportMode): MapLibreNavigationOptions {
    val defaults = MapLibreNavigationOptions()
    val factor =
        when (mode) {
            TransportMode.WALK -> WALK_OFF_ROUTE_FACTOR
            TransportMode.BIKE -> BIKE_OFF_ROUTE_FACTOR
            else -> 1.0
        }
    return defaults.copy(offRouteThresholdRadiusMeters = defaults.offRouteThresholdRadiusMeters * factor)
}

private const val HEADING_TOLERANCE = 45
private const val ARRIVAL_METERS = 25.0
private const val REROUTE_PAUSE_MILLIS = 5_000L
private const val WALK_OFF_ROUTE_FACTOR = 2.0
private const val BIKE_OFF_ROUTE_FACTOR = 1.5
