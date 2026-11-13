package nz.eloque.openmaps.ui.directions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.MapLine

/** Draws [itinerary] on the map and fits the camera to it while this is composed. */
@Composable
fun ShowItineraryOnMap(
    map: MapController,
    itinerary: Itinerary?,
) {
    val transitColor = MaterialTheme.colorScheme.primary
    val walkColor = MaterialTheme.colorScheme.outline
    DisposableEffect(itinerary) {
        val lines = itinerary?.legs.orEmpty().map { it.toMapLine(transitColor, walkColor) }
        map.lines = lines
        onDispose { if (map.lines === lines) map.lines = emptyList() }
    }
    LaunchedEffect(itinerary) {
        itinerary?.let { map.fit(it.legs.flatMap(Leg::geometry)) }
    }
}

private fun Leg.toMapLine(
    transitColor: Color,
    walkColor: Color,
): MapLine =
    when (this) {
        is Leg.Transit -> MapLine(geometry, line.color?.let(::Color) ?: transitColor)
        is Leg.Street -> MapLine(geometry, walkColor, width = 4.dp)
    }
