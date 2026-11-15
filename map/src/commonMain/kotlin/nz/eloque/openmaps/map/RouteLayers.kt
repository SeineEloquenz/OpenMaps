package nz.eloque.openmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.IconTextFit
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.DpPadding
import org.maplibre.compose.util.ImageStretch
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A route the user can pick on the map, labelled with [label]. The [selected] one is drawn through [MapController.lines]. */
data class MapRoute(
    val id: String,
    val points: List<LatLng>,
    val label: String,
    val selected: Boolean,
)

/** Colours of the route labels and of routes that are not selected. */
internal data class RouteColors(
    val other: Color,
    val casing: Color,
    val label: Color,
    val labelText: Color,
    val selectedLabel: Color,
    val selectedLabelText: Color,
)

/** The routes that are not selected, drawn below the selected one. */
@Composable
internal fun OtherRouteLines(
    routes: List<MapRoute>,
    colors: RouteColors,
    onClick: (String) -> Unit,
) {
    val others = routes.filterNot { it.selected || it.points.size < 2 }
    if (others.isEmpty()) return
    val features =
        others.map { route ->
            Feature(LineString(route.points.map { it.toPosition() }), buildJsonObject { put(ROUTE_INDEX, routes.indexOf(route)) })
        }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val handler = routeClickHandler(routes, onClick)
    LineLayer(
        id = "other-routes-casing",
        source = source,
        color = const(colors.casing),
        width = const(9.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        onClick = handler,
    )
    LineLayer(
        id = "other-routes",
        source = source,
        color = const(colors.other),
        width = const(6.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        onClick = handler,
    )
}

/** Time bubbles on each route, the selected one on top. */
@Composable
internal fun RouteLabels(
    routes: List<MapRoute>,
    colors: RouteColors,
    onClick: (String) -> Unit,
) {
    val labelled = routes.filter { it.points.size >= 2 }
    if (labelled.isEmpty()) return
    val handler = routeClickHandler(routes, onClick)
    listOf(false, true).forEach { selected ->
        val group = labelled.filter { it.selected == selected }
        if (group.isEmpty()) return@forEach
        val features =
            group.map { route ->
                val index = routes.indexOf(route)
                Feature(
                    Point(pointAlong(route.points, LabelFractions[index % LabelFractions.size]).toPosition()),
                    buildJsonObject {
                        put(ROUTE_INDEX, index)
                        put(ROUTE_LABEL, route.label)
                    },
                )
            }
        val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
        val fill = if (selected) colors.selectedLabel else colors.label
        val bubble = remember(fill, colors.casing) { BubblePainter(fill, colors.casing) }
        SymbolLayer(
            id = if (selected) "selected-route-label" else "route-labels",
            source = source,
            iconImage = image(bubble, size = DpSize(BubbleSize, BubbleSize), stretch = BubbleStretch),
            iconTextFit = const(IconTextFit.Both),
            iconTextFitPadding = const(DpPadding(2.dp, 6.dp, 2.dp, 6.dp)),
            iconAllowOverlap = const(selected),
            textField = feature[ROUTE_LABEL].asString().cast(),
            textFont = const(listOf("Noto Sans Bold")),
            textSize = const(0.8f.em),
            textColor = const(if (selected) colors.selectedLabelText else colors.labelText),
            textAllowOverlap = const(selected),
            onClick = handler,
        )
    }
}

private fun routeClickHandler(
    routes: List<MapRoute>,
    onClick: (String) -> Unit,
): FeaturesClickHandler =
    { clicked ->
        val index = clicked.firstOrNull()?.properties?.get(ROUTE_INDEX)?.jsonPrimitive?.intOrNull
        val route = index?.let(routes::getOrNull)
        if (route == null) {
            ClickResult.Pass
        } else {
            onClick(route.id)
            ClickResult.Consume
        }
    }

/** The point [fraction] of the way along [points], measured on the ground. */
internal fun pointAlong(
    points: List<LatLng>,
    fraction: Double,
): LatLng {
    val segments = points.zipWithNext { a, b -> a to b }
    val lengths = segments.map { (a, b) -> flatDistance(a, b) }
    var remaining = lengths.sum() * fraction
    segments.forEachIndexed { index, (a, b) ->
        val length = lengths[index]
        if (remaining <= length && length > 0) {
            val t = remaining / length
            return LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
        }
        remaining -= length
    }
    return points.last()
}

private fun flatDistance(
    a: LatLng,
    b: LatLng,
): Double {
    val dx = (b.longitude - a.longitude) * cos((a.latitude + b.latitude) / 2 * PI / 180)
    val dy = b.latitude - a.latitude
    return sqrt(dx * dx + dy * dy)
}

private class BubblePainter(
    private val fill: Color,
    private val outline: Color,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        val radius = CornerRadius(size.minDimension * 0.35f)
        val inset = 1.dp.toPx()
        drawRoundRect(
            fill,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = radius,
        )
        drawRoundRect(
            outline,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = radius,
            style = Stroke(inset),
        )
    }
}

private const val ROUTE_INDEX = "r"
private const val ROUTE_LABEL = "l"
private val BubbleSize = 16.dp
private val BubbleStretch = ImageStretch.capInsets(6.dp, 6.dp, 6.dp, 6.dp)

/** Where along each route its label sits, varied so labels of routes sharing a stretch do not stack. */
private val LabelFractions = listOf(0.5, 0.35, 0.65, 0.42, 0.58)
