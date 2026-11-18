package nz.eloque.openmaps.car

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.DurationSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.RouteFeature
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.StreetRequest
import nz.eloque.openmaps.core.model.StreetRouter
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.map.CarRouteColor
import nz.eloque.openmaps.map.MapLine
import nz.eloque.openmaps.ui.guidance.ActiveTrip
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The ways to drive to a destination, to pick one and start. */
internal class RoutePreviewScreen(
    carContext: CarContext,
    private val home: HomeScreen,
    private var destination: CarDestination,
) : Screen(carContext),
    KoinComponent {
    private val router: StreetRouter by inject()
    private val geocoder: Geocoder by inject()
    private val settings: SettingsStore by inject()
    private val places: PlacesStore by inject()
    private val active: ActiveTrip by inject()
    private var routes: List<Itinerary>? = null
    private var message: Int? = null
    private var selected = 0
    private var starting = false

    init {
        lifecycleScope.launch { plan() }
        whileShown(this, onShown = ::showOnMap, onHidden = { home.mapLent = false })
    }

    private suspend fun plan() {
        try {
            val target =
                when (val wanted = destination) {
                    is CarDestination.At -> wanted
                    is CarDestination.Search ->
                        geocoder.search(wanted.query, home.position).firstOrNull()?.toDestination()
                            ?: return fail(R.string.car_nothing_found)
                }
            destination = target
            val from = home.position ?: return fail(R.string.car_no_location)
            val found =
                router.route(
                    StreetRequest(
                        from = from,
                        to = target.location,
                        modes = setOf(TransportMode.CAR),
                        time = null,
                        options = settings.routeOptions.value,
                        alternatives = true,
                    ),
                )
            if (found.isEmpty()) return fail(R.string.car_no_route)
            routes = found
            showOnMap()
            invalidate()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fail(R.string.car_no_route)
        }
    }

    private fun fail(text: Int) {
        message = text
        invalidate()
    }

    private fun showOnMap() {
        val found = routes ?: return
        home.mapLent = true
        val lines = found.map { it.legs.flatMap(Leg::geometry) }
        home.map.markers = listOfNotNull((destination as? CarDestination.At)?.location)
        home.map.lines =
            lines.withIndex().sortedBy { it.index == selected }.map { (index, points) ->
                MapLine(points, if (index == selected) CarRouteColor else OtherRouteColor, LINE_WIDTH.dp)
            }
        home.map.fit(lines.flatten())
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val target = destination as? CarDestination.At
        message?.let { return MessageTemplate.Builder(carContext.getString(it)).setHeaderAction(Action.BACK).build() }
        val builder =
            RoutePreviewNavigationTemplate.Builder()
                .setTitle(target?.let { carContext.getString(R.string.car_routes, it.name) } ?: carContext.getString(R.string.car_loading))
                .setHeaderAction(Action.BACK)
                .setMapActionStrip(mapActions(home))
                .setPanModeListener { }
        val found = routes
        if (found == null || target == null || starting) return builder.setLoading(true).build()
        val list =
            ItemList.Builder().setOnSelectedListener { index ->
                selected = index
                showOnMap()
                invalidate()
            }
        val unit = settings.distanceUnit.value
        found.forEachIndexed { index, route ->
            val street = route.legs.filterIsInstance<Leg.Street>()
            val text = SpannableStringBuilder(distanceText(street.sumOf { it.distanceMeters ?: 0.0 }, unit))
            val features = street.flatMap { it.features }.toSet()
            listOf(
                RouteFeature.TOLLS to R.string.car_tolls,
                RouteFeature.MOTORWAYS to R.string.car_motorways,
                RouteFeature.FERRIES to R.string.car_ferries,
            ).filter { it.first in features }.forEach { text.append(" · ").append(carContext.getString(it.second)) }
            if (index == 0) text.append(" · ").append(carContext.getString(R.string.car_route_fastest))
            list.addItem(
                Row.Builder()
                    .setTitle(
                        SpannableString(" ").apply {
                            setSpan(DurationSpan.create(route.duration.inWholeSeconds), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                        },
                    ).addText(text)
                    .build(),
            )
        }
        list.setSelectedIndex(selected.coerceIn(0, found.lastIndex))
        return builder
            .setItemList(list.build())
            .setNavigateAction(Action.Builder().setTitle(carContext.getString(R.string.car_go)).setOnClickListener(::start).build())
            .build()
    }

    private fun start() {
        val target = destination as? CarDestination.At ?: return
        val from = home.position ?: return
        val route = routes?.getOrNull(selected)
        starting = true
        invalidate()
        lifecycleScope.launch {
            try {
                home.navigator.start(
                    from = from,
                    to = target.location,
                    mode = TransportMode.CAR,
                    options = settings.routeOptions.value,
                    prefer = route?.takeIf { selected > 0 }?.legs?.flatMap(Leg::geometry),
                )
                active.drivingTo(target.name, target.location.latitude, target.location.longitude)
                target.place?.let { places.addRecent(it) }
                screenManager.popToRoot()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                starting = false
                CarToast.makeText(carContext, R.string.car_no_route, CarToast.LENGTH_LONG).show()
                invalidate()
            }
        }
    }
}

private val OtherRouteColor = Color(0xFF9AA0A6)
private const val LINE_WIDTH = 7
