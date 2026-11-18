package nz.eloque.openmaps.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.CarMap
import nz.eloque.openmaps.map.CarRouteColor
import nz.eloque.openmaps.map.MapLine
import nz.eloque.openmaps.map.NavigationPuck
import nz.eloque.openmaps.ui.guidance.ActiveTrip
import nz.eloque.openmaps.ui.guidance.split
import nz.eloque.openmaps.ui.navigation.NavigationKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The car's main screen: a map that follows the car, and turn-by-turn guidance while a trip runs. */
internal class HomeScreen(
    carContext: CarContext,
    /** The car session's lifecycle, which keeps the map drawing while other screens cover this one. */
    session: Lifecycle,
) : Screen(carContext),
    KoinComponent {
    val navigator: Navigator by inject()
    private val settings: SettingsStore by inject()
    private val locations: LocationUpdates by inject()
    private val http: HttpClient by inject()
    private val active: ActiveTrip by inject()
    private val navigation = carContext.getCarService(NavigationManager::class.java)

    val guidance = CarGuidance(carContext)
    val map = CarMap(lifecycleScope, { url -> http.get(url).bodyAsText() }, carContext.isDarkMode)
    private val surface =
        CarMapSurface(carContext, session, map) {
            if (following) {
                following = false
                invalidate()
            }
        }

    /** The car's latest known position. */
    var position: LatLng? = null
        private set

    /** True while another screen draws its own lines and markers on the map. */
    var mapLent = false
        set(value) {
            field = value
            if (!value) redraw(navigator.state.value)
        }

    private var following = true
    private var navigating = false

    init {
        navigation.setNavigationManagerCallback(
            object : NavigationManagerCallback {
                override fun onStopNavigation() = navigator.stop()
            },
        )
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (navigating) navigation.navigationEnded()
                    navigation.clearNavigationManagerCallback()
                }
            },
        )
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                locations.updates().catch { }.collect { fix ->
                    val first = position == null
                    position = fix.location
                    if (navigator.state.value == null && !mapLent) {
                        map.puck = NavigationPuck(fix.location, null)
                        if (following) map.show(fix.location, if (first) FREE_ZOOM else map.state.cameraPosition.zoom)
                    }
                    if (first) invalidate()
                }
            }
        }
        lifecycleScope.launch { navigator.state.collect(::onState) }
    }

    fun onDarkModeChanged(dark: Boolean) {
        map.setDark(dark)
        surface.updateConfiguration(carContext.resources.configuration)
    }

    private fun onState(state: NavigationState?) {
        if (state == null) {
            if (navigating) {
                navigating = false
                navigation.navigationEnded()
            }
        } else {
            if (!navigating) {
                navigating = true
                following = true
                navigation.navigationStarted()
            }
            if (state.status != NavigationStatus.ARRIVED) {
                navigation.updateTrip(guidance.trip(state, destinationName(), settings.distanceUnit.value))
            }
        }
        redraw(state)
        invalidate()
    }

    private fun redraw(state: NavigationState?) {
        if (mapLent) return
        map.markers = emptyList()
        if (state == null) {
            map.lines = emptyList()
            map.puck = position?.let { NavigationPuck(it, null) }
            return
        }
        val (done, ahead) = split(state.route, state.travelledMeters)
        map.lines = listOf(MapLine(done, TravelledColor, LINE_WIDTH.dp), MapLine(ahead, CarRouteColor, LINE_WIDTH.dp))
        val at = state.position ?: return
        map.puck = NavigationPuck(at, state.bearing)
        if (following && state.status != NavigationStatus.ARRIVED) map.follow(at, state.bearing, DRIVE_ZOOM, DRIVE_TILT)
    }

    private fun destinationName(): String? = (active.key as? NavigationKey)?.to?.name

    fun recenter() {
        following = true
        val state = navigator.state.value
        val at = state?.position ?: position
        if (at != null) {
            if (state != null) map.follow(at, state.bearing, DRIVE_ZOOM, DRIVE_TILT) else map.show(at, FREE_ZOOM)
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        if (surface.failed) {
            return MessageTemplate.Builder(carContext.getString(R.string.car_map_unavailable)).setHeaderAction(Action.APP_ICON).build()
        }
        val state = navigator.state.value
        val builder =
            NavigationTemplate.Builder()
                .setBackgroundColor(CarColor.createCustom(TEMPLATE_LIGHT, TEMPLATE_DARK))
                .setMapActionStrip(mapActions(this))
                .setPanModeListener { }
        if (state == null) {
            builder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(guidance.icon(R.drawable.car_search))
                            .setOnClickListener { screenManager.push(SearchScreen(carContext, this)) }
                            .build(),
                    ).addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.car_where_to))
                            .setOnClickListener { screenManager.push(PlacesScreen(carContext, this)) }
                            .build(),
                    ).build(),
            )
            return builder.build()
        }
        val unit = settings.distanceUnit.value
        if (state.status == NavigationStatus.ARRIVED) {
            builder.setNavigationInfo(MessageInfo.Builder(carContext.getString(R.string.car_arrived)).build())
        } else {
            builder.setNavigationInfo(guidance.routingInfo(state, unit)).setDestinationTravelEstimate(guidance.estimate(state, unit))
        }
        builder.setActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.car_add_stop))
                        .setOnClickListener { screenManager.push(PlacesScreen(carContext, this)) }
                        .build(),
                ).addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.car_stop))
                        .setOnClickListener { navigator.stop() }
                        .build(),
                ).build(),
        )
        return builder.build()
    }
}

/** Pan, zoom and recenter buttons beside the map. */
internal fun mapActions(home: HomeScreen): ActionStrip =
    ActionStrip.Builder()
        .addAction(Action.PAN)
        .addAction(
            Action.Builder().setIcon(home.guidance.icon(R.drawable.car_add)).setOnClickListener { home.map.zoomBy(1) }.build(),
        ).addAction(
            Action.Builder().setIcon(home.guidance.icon(R.drawable.car_remove)).setOnClickListener { home.map.zoomBy(-1) }.build(),
        ).addAction(
            Action.Builder()
                .setIcon(home.guidance.icon(R.drawable.car_my_location))
                .setOnClickListener { home.recenter() }
                .build(),
        ).build()

private val TravelledColor = Color(0xFF9E9E9E)
private const val LINE_WIDTH = 8
private const val FREE_ZOOM = 15.0
private const val DRIVE_ZOOM = 16.5
private const val DRIVE_TILT = 50.0
private const val TEMPLATE_LIGHT = 0xFF1A73E8.toInt()
private const val TEMPLATE_DARK = 0xFF0B57D0.toInt()
