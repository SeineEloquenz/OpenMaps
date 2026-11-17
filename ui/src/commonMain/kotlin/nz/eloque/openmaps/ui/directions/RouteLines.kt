package nz.eloque.openmaps.ui.directions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.MapLine
import nz.eloque.openmaps.map.MapRoute
import nz.eloque.openmaps.ui.components.formatDuration

/** Draws [itinerary] on the map while this is composed, fitting the camera to it when [fit] is set. */
@Composable
fun ShowItineraryOnMap(
    map: MapController,
    itinerary: Itinerary?,
    fit: Boolean = true,
) {
    val transitColor = MaterialTheme.colorScheme.primary
    val walkColor = MaterialTheme.colorScheme.outline
    DisposableEffect(itinerary) {
        val lines = itinerary?.legs.orEmpty().map { it.toMapLine(transitColor, walkColor) }
        map.lines = lines
        onDispose { if (map.lines === lines) map.lines = emptyList() }
    }
    LaunchedEffect(itinerary) {
        if (fit) itinerary?.let { map.fit(it.legs.flatMap(Leg::geometry)) }
    }
}

/** Draws distinct routes among [itineraries] to choose from, labelled with their duration, and fits the camera to them. */
@Composable
fun ShowRouteChoicesOnMap(
    map: MapController,
    itineraries: List<Itinerary>,
    selected: Itinerary?,
    onSelect: (Itinerary) -> Unit,
) {
    val choices = (itineraries.distinctBy { it.pattern }.take(MAX_ROUTE_CHOICES) + listOfNotNull(selected)).distinctBy { it.id }
    val routes =
        choices.map { itinerary ->
            MapRoute(itinerary.id, itinerary.legs.flatMap(Leg::geometry), formatDuration(itinerary.duration), itinerary.id == selected?.id)
        }
    val currentOnSelect by rememberUpdatedState(onSelect)
    DisposableEffect(routes) {
        map.routes = routes
        map.onRouteClick = { id -> choices.firstOrNull { it.id == id }?.let(currentOnSelect) }
        onDispose {
            if (map.routes === routes) map.routes = emptyList()
            map.onRouteClick = null
        }
    }
    LaunchedEffect(choices.map { it.id }.toSet() - setOfNotNull(selected?.id)) {
        map.fit(choices.flatMap { it.legs.flatMap(Leg::geometry) })
    }
}

/** The lines a route takes, so the same route at another time counts once. Routes without public transport each count. */
private val Itinerary.pattern: List<String>
    get() =
        if (legs.none { it is Leg.Transit }) {
            listOf(id)
        } else {
            legs.map { leg -> if (leg is Leg.Transit) "${leg.line.mode}:${leg.line.shortName}:${leg.from.name}" else leg.mode.name }
        }

private const val MAX_ROUTE_CHOICES = 3

private fun Leg.toMapLine(
    transitColor: Color,
    walkColor: Color,
): MapLine =
    when (this) {
        is Leg.Transit -> MapLine(geometry, line.color?.let(::Color) ?: transitColor)
        is Leg.Street -> if (mode == TransportMode.WALK) MapLine(geometry, walkColor, width = 4.dp) else MapLine(geometry, transitColor)
    }
