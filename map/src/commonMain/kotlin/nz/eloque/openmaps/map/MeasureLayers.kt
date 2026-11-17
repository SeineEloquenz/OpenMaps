package nz.eloque.openmaps.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.MultiPoint

/** The measured line through [points], the last one being the map center, with a dot at each point set. */
@Composable
internal fun MeasureLayers(
    points: List<LatLng>,
    color: Color,
) {
    if (points.isEmpty()) return
    val positions = points.map { it.toPosition() }
    val line = rememberGeoJsonSource(GeoJsonData.Features(LineString(if (positions.size >= 2) positions else positions + positions)))
    val dots = rememberGeoJsonSource(GeoJsonData.Features(MultiPoint(positions.dropLast(1).ifEmpty { positions })))
    LineLayer(id = "measure-line", source = line, color = const(color), width = const(3.dp))
    CircleLayer(
        id = "measure-points",
        source = dots,
        color = const(Color.White),
        radius = const(5.dp),
        strokeColor = const(color),
        strokeWidth = const(2.dp),
    )
}

/** Marks the map center, where the next measured point goes. */
@Composable
internal fun Crosshair(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.align(Alignment.Center).size(36.dp))
        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp))
    }
}

/** The track being recorded, as a line ending at the latest point. */
@Composable
internal fun RecordingLayer(
    points: List<LatLng>,
    color: Color,
) {
    if (points.size < 2) return
    val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(points.map { it.toPosition() })))
    LineLayer(id = "recording-line", source = source, color = const(color), width = const(4.dp))
}
