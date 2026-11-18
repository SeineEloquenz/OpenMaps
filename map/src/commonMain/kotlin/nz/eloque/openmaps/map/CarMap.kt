package nz.eloque.openmaps.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point

/** The map of a car screen with route lines and the user's position, drawn without the phone's controls. Main thread only. */
class CarMap(
    private val scope: CoroutineScope,
    private val loadStyle: suspend (String) -> String,
    dark: Boolean,
) : AutoCloseable {
    /** Drawn in order, so later lines lie on top. */
    var lines: List<MapLine> by mutableStateOf(emptyList(), referentialEqualityPolicy())

    var puck: NavigationPuck? by mutableStateOf(null)

    /** Places listed on the car screen, numbered in the order given. */
    var markers: List<LatLng> by mutableStateOf(emptyList(), referentialEqualityPolicy())

    val state: MapState =
        DefaultMapRuntime.instance.createMapState(
            baseStyle = if (dark) LoadingDarkStyle else BaseStyle.Uri(LIGHT_STYLE),
            cameraPosition = CameraPosition(center = LatLng(50.0, 10.0).toPosition(), zoom = 3.5),
        ) { Content() }

    private var styleJob: Job? = null
    private var camera: Job? = null

    init {
        setDark(dark)
    }

    fun setDark(dark: Boolean) {
        styleJob?.cancel()
        val style = state.style.asMutable ?: return
        if (!dark) {
            style.baseStyle = BaseStyle.Uri(LIGHT_STYLE)
            return
        }
        styleJob = scope.launch { darkStyles { loadStyle(LIGHT_STYLE) }.collect { style.baseStyle = it } }
    }

    fun follow(
        position: LatLng,
        bearing: Double?,
        zoom: Double,
        tilt: Double,
    ) = animate(CameraUpdate(center = position.toPosition(), zoom = zoom, bearing = bearing, pitch = tilt), FollowAnimation)

    fun show(
        position: LatLng,
        zoom: Double = state.cameraPosition.zoom.coerceAtLeast(14.0),
    ) = animate(CameraUpdate(center = position.toPosition(), zoom = zoom, bearing = 0.0, pitch = 0.0))

    fun zoomBy(levels: Int) = animate(CameraUpdate(zoom = state.cameraPosition.zoom + levels))

    fun fit(points: List<LatLng>) {
        if (points.isEmpty()) return
        camera?.cancel()
        camera =
            scope.launch {
                val box =
                    BoundingBox(
                        west = points.minOf { it.longitude },
                        south = points.minOf { it.latitude },
                        east = points.maxOf { it.longitude },
                        north = points.maxOf { it.latitude },
                    )
                state.animateCameraToBounds(box, CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)))
            }
    }

    private fun animate(
        update: CameraUpdate,
        duration: kotlin.time.Duration = FollowAnimation,
    ) {
        camera?.cancel()
        camera = scope.launch { state.animateCamera(update, CameraAnimation.Ease { this.duration = duration }) }
    }

    override fun close() {
        styleJob?.cancel()
        camera?.cancel()
        state.close()
    }

    @androidx.compose.runtime.Composable
    private fun Markers() {
        if (markers.isEmpty()) return
        val features =
            markers.mapIndexed { index, location ->
                Feature(Point(location.toPosition()), buildJsonObject { put(MARKER_NUMBER, (index + 1).toString()) })
            }
        val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
        CircleLayer(
            id = "car-markers",
            source = source,
            color = const(CarRouteColor),
            radius = const(12.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
        )
        SymbolLayer(
            id = "car-marker-numbers",
            source = source,
            textField = feature[MARKER_NUMBER].asString().cast(),
            textFont = const(listOf("Noto Sans Bold")),
            textSize = const(0.9f.em),
            textColor = const(Color.White),
            textAllowOverlap = const(true),
            textIgnorePlacement = const(true),
        )
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        lines.filter { it.points.size >= 2 }.forEachIndexed { index, line ->
            androidx.compose.runtime.key(index, line) {
                val id = "car-$index-${line.hashCode()}"
                val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(line.points.map { it.toPosition() })))
                LineLayer(
                    id = "$id-casing",
                    source = source,
                    color = const(Color.White),
                    width = const(line.width + 3.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = id,
                    source = source,
                    color = const(line.color),
                    width = const(line.width),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
        }
        Markers()
        val puck = puck ?: return
        val source = rememberGeoJsonSource(GeoJsonData.Features(Point(puck.position.toPosition())))
        if (puck.bearing == null) {
            CircleLayer(
                id = "car-position",
                source = source,
                color = const(CarRouteColor),
                radius = const(10.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(3.dp),
            )
            return
        }
        val painter = rememberVectorPainter(Icons.Default.Navigation)
        val icon = image(remember(painter) { BadgePainter(painter, CarRouteColor, Color.White) }, size = DpSize(36.dp, 36.dp))
        SymbolLayer(
            id = "car-position",
            source = source,
            iconImage = icon,
            iconRotate = const(puck.bearing.toFloat()),
            iconRotationAlignment = const(IconRotationAlignment.Map),
            iconPitchAlignment = const(IconPitchAlignment.Viewport),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
        )
    }
}

/** The colour of the route ahead on car screens, which have no app theme to take it from. */
val CarRouteColor = Color(0xFF1A73E8)
private const val MARKER_NUMBER = "n"
