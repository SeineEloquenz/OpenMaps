package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import nz.eloque.openmaps.core.model.Lane
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
import kotlin.time.Duration.Companion.minutes
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
    @SerialName("search_cutoff") val searchCutoff: Int,
    val heading: Int? = null,
    @SerialName("heading_tolerance") val headingTolerance: Int? = null,
)

/** Routes with banner and voice instructions in the Mapbox Directions format MapLibre Navigation reads. */
internal class ValhallaNavigationRoutes(
    private val client: HttpClient,
    private val servers: ValhallaServers,
    private val language: () -> String?,
    private val units: () -> DistanceUnit,
) {
    fun isConfigured(mode: TransportMode) = costing(mode) != null && servers.urls(mode).isNotEmpty()

    /** Speed limits along [route] for driving, from the road segments it is matched to on the server it came from. */
    suspend fun speedLimits(route: DirectionsRoute): SpeedLimits {
        val server = checkNotNull(route.routeOptions?.baseUrl) { "The route has no server" }
        val body =
            TraceAttributesRequestDto(
                encodedPolyline = route.geometry,
                costing = "auto",
                shapeMatch = "edge_walk",
                filters =
                    TraceFiltersDto(
                        attributes = listOf("edge.speed_limit", "edge.begin_shape_index", "edge.end_shape_index"),
                        action = "include",
                    ),
            )
        val edges: TraceAttributesDto =
            servers.at(server) { url ->
                client
                    .post("$url/trace_attributes") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.body()
            }
        return SpeedLimits.of(route.points(), edges.edges)
    }

    /** Null when Valhalla finds no route. [heading] keeps a rerouted trip going the way the user faces. */
    suspend fun route(
        from: LatLng,
        heading: Float?,
        to: LatLng,
        mode: TransportMode,
        options: RouteOptions,
    ): DirectionsRoute? {
        val costing = checkNotNull(costing(mode)) { "No street routing for $mode" }
        val start =
            NavigationLocationDto(
                from.latitude,
                from.longitude,
                SearchFilterDto(excludeClosures = false),
                SEARCH_CUTOFF_METERS,
                heading = heading?.toInt(),
                headingTolerance = heading?.let { HEADING_TOLERANCE },
            )
        val body =
            NavigationRequestDto(
                locations =
                    listOf(
                        start,
                        NavigationLocationDto(to.latitude, to.longitude, SearchFilterDto(excludeClosures = false), SEARCH_CUTOFF_METERS),
                    ),
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
        val (url, route) =
            servers.first(mode) { url ->
                val text =
                    try {
                        client
                            .post("$url/route") {
                                contentType(ContentType.Application.Json)
                                setBody(body)
                            }.bodyAsText()
                    } catch (e: ClientRequestException) {
                        if (e.response.status == HttpStatusCode.BadRequest) return@first null
                        throw e
                    }
                DirectionsResponse.fromJson(text).routes.firstOrNull()?.let { url to it }
            } ?: return null
        val language = body.language
        return route.let { route ->
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

    override fun isAvailable(mode: TransportMode) = routes.isConfigured(mode)

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
        var lastLocation: Location? = null,
        var recheck: Job? = null,
        var limits: SpeedLimits? = null,
        var routeVersion: Int = 0,
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
        loadLimits(trip, route)
        if (mode == TransportMode.CAR && replay == null) trip.recheck = scope.launch { recheck(trip) }
    }

    /** Asks for the route again now and then, so closures and roadworks reported since the start change it. */
    private suspend fun recheck(trip: Trip) {
        while (true) {
            delay(RECHECK_INTERVAL)
            if (this.trip !== trip) return
            val here = trip.lastLocation ?: continue
            val remaining = _state.value?.takeIf { it.status == NavigationStatus.NAVIGATING }?.remainingTime ?: continue
            val route =
                runCatching { routes.route(LatLng(here.latitude, here.longitude), here.bearing, trip.to, trip.mode, trip.options) }
                    .getOrNull() ?: continue
            if (this.trip !== trip || trip.rerouting) return
            // The current estimate knows nothing of new closures, so a fresh route that differs a lot either way is better informed.
            if ((route.duration.seconds - remaining).absoluteValue > RECHECK_DIFFERENCE) {
                trip.geometry = route.points()
                trip.navigation.startNavigation(route)
                loadLimits(trip, route)
            }
        }
    }

    override fun stop() {
        trip?.let {
            it.recheck?.cancel()
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
        trip.lastLocation = location
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
                instruction = step?.upcoming(progress.stepDistanceRemaining, next)?.copy(lanes = progress.lanesAhead()),
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
                speedLimit = trip.limits?.at(progress.distanceTraveled),
                speed = location.speedMetersPerSeconds?.toDouble(),
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
            loadLimits(trip, route)
        }
    }

    /** Fetches the speed limits for a car trip's new [route], replacing those of the route before. */
    private fun loadLimits(
        trip: Trip,
        route: DirectionsRoute,
    ) {
        trip.limits = null
        val version = ++trip.routeVersion
        if (trip.mode != TransportMode.CAR) return
        scope.launch {
            val limits = runCatching { routes.speedLimits(route) }.getOrNull()
            if (this@ValhallaNavigator.trip === trip && trip.routeVersion == version) trip.limits = limits
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

/** Lanes at the next intersection ahead that has lane data, once it is close. */
private fun RouteProgress.lanesAhead(): List<Lane> {
    val intersection = upcomingIntersection ?: return emptyList()
    val lanes = intersection.lanes?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val step = currentLeg.steps.getOrNull(stepIndex) ?: return emptyList()
    val travelled = step.distance - stepDistanceRemaining
    val ahead = (intersectionDistancesAlongStep?.get(intersection) ?: return emptyList()) - travelled
    return if (ahead in 0.0..LANES_AHEAD_METERS) lanes.map { it.toLane() } else emptyList()
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

private val RECHECK_INTERVAL = 5.minutes
private val RECHECK_DIFFERENCE = 1.minutes
private const val HEADING_TOLERANCE = 45
private const val LANES_AHEAD_METERS = 500.0
private const val ARRIVAL_METERS = 25.0
private const val REROUTE_PAUSE_MILLIS = 5_000L
private const val WALK_OFF_ROUTE_FACTOR = 2.0
private const val BIKE_OFF_ROUTE_FACTOR = 1.5
