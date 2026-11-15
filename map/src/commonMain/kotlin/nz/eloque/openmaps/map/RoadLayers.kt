package nz.eloque.openmaps.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Construction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.RoadEventTiles
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.GeometryType
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.VectorTileSource
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.MultiLineString
import org.maplibre.spatialk.geojson.MultiPoint
import org.maplibre.spatialk.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/** Roadworks and closures as coloured lines, and as badges where only a point is known. */
@Composable
internal fun RoadLayers(
    tiles: RoadEventTiles?,
    dark: Boolean,
    onClick: (Map<String, String>) -> Unit,
) {
    if (tiles == null) return
    val source =
        remember(tiles) {
            VectorTileSource(roadSourceId(tiles), listOf(tiles.urlTemplate)) {
                minZoom = tiles.minZoom
                maxZoom = tiles.maxZoom
                attributionHtml = tiles.attribution
            }
        }
    val outline = if (dark) PinRingDark else Color.White
    val color = switch(feature[tiles.closedProperty].convertToString(), case("true", const(ClosedColor)), fallback = const(WorksColor))
    val closedPainter = rememberVectorPainter(Icons.Default.Block)
    val worksPainter = rememberVectorPainter(Icons.Default.Construction)
    val closedBadge = image(remember(closedPainter, outline) { BadgePainter(closedPainter, ClosedColor, outline) }, size = BadgeSize)
    val worksBadge = image(remember(worksPainter, outline) { BadgePainter(worksPainter, WorksColor, outline) }, size = BadgeSize)
    val handler: FeaturesClickHandler = { clicked ->
        val properties = clicked.firstOrNull()?.properties
        if (properties == null) {
            ClickResult.Pass
        } else {
            onClick(properties.asText())
            ClickResult.Consume
        }
    }
    val lines = any(feature.geometryType() eq const(GeometryType.LineString), feature.geometryType() eq const(GeometryType.MultiLineString))
    val points = any(feature.geometryType() eq const(GeometryType.Point), feature.geometryType() eq const(GeometryType.MultiPoint))
    LineLayer(
        id = ROAD_LINES,
        source = source,
        sourceLayer = tiles.layer,
        minZoom = LINES_MIN_ZOOM,
        filter = lines,
        color = color,
        opacity = switch(feature[tiles.exactProperty].convertToString(), case("false", const(0.55f)), fallback = const(0.9f)),
        width = const(4.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        onClick = handler,
    )
    // Nearly transparent rather than invisible, which would drop it from tap hit tests.
    LineLayer(
        id = ROAD_LINE_TARGETS,
        source = source,
        sourceLayer = tiles.layer,
        minZoom = LINES_MIN_ZOOM,
        filter = lines,
        color = const(Color.Black.copy(alpha = 0.01f)),
        width = const(24.dp),
        onClick = handler,
    )
    SymbolLayer(
        id = ROAD_BADGES,
        source = source,
        sourceLayer = tiles.layer,
        minZoom = BADGES_MIN_ZOOM,
        filter = points,
        iconImage = switch(feature[tiles.closedProperty].convertToString(), case("true", closedBadge), fallback = worksBadge),
        iconAllowOverlap = const(true),
        onClick = handler,
    )
}

internal fun JsonObject.asText(): Map<String, String> =
    mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeUnless { it.content == "null" }?.let { key to it.content } }.toMap()

/** Whether any point of [geometry] lies within [meters] of the line through [route]. */
internal fun Geometry.near(
    route: List<LatLng>,
    meters: Double,
): Boolean {
    val points =
        when (this) {
            is Point -> listOf(coordinates)
            is MultiPoint -> coordinates
            is LineString -> coordinates
            is MultiLineString -> coordinates.flatten()
            else -> emptyList()
        }
    return points.any { p -> distanceToLine(LatLng(p.latitude, p.longitude), route) <= meters }
}

/** Metres from [point] to the nearest segment of [line], flat-earth, accurate for short distances. */
internal fun distanceToLine(
    point: LatLng,
    line: List<LatLng>,
): Double {
    if (line.isEmpty()) return Double.MAX_VALUE
    val scaleX = METERS_PER_DEGREE * cos(point.latitude * PI / 180)

    fun x(p: LatLng) = (p.longitude - point.longitude) * scaleX

    fun y(p: LatLng) = (p.latitude - point.latitude) * METERS_PER_DEGREE
    if (line.size == 1) return hypot(x(line[0]), y(line[0]))
    return line.zipWithNext().minOf { (a, b) ->
        val ax = x(a)
        val ay = y(a)
        val dx = x(b) - ax
        val dy = y(b) - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        hypot(ax + t * dx, ay + t * dy)
    }
}

internal fun roadSourceId(tiles: RoadEventTiles) = "road-events-${tiles.hashCode()}"

private const val ROAD_LINES = "road-event-lines"
private const val ROAD_BADGES = "road-event-badges"
private const val ROAD_LINE_TARGETS = "road-event-line-targets"
private const val LINES_MIN_ZOOM = 7f
private const val BADGES_MIN_ZOOM = 11f
private const val METERS_PER_DEGREE = 111_320.0
private val BadgeSize = DpSize(20.dp, 20.dp)
private val ClosedColor = Color(0xFFD32F2F)
private val WorksColor = Color(0xFFF57C00)
