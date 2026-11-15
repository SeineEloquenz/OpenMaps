package nz.eloque.openmaps.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** A line drawn on the map, such as one leg of a route. */
@Immutable
data class MapLine(
    val points: List<LatLng>,
    val color: Color,
    val width: Dp = 6.dp,
)

/** Map camera, user location, the highlighted marker and drawn lines, shared between [OpenMap] and the UI around it. */
@Stable
class MapController internal constructor(
    internal val mapState: MapState,
    internal val location: LocationState,
    private val markerState: MutableState<LatLng?>,
    private val linesState: MutableState<List<MapLine>>,
    private val stopsState: MutableState<List<SearchResult.Stop>>,
    private val resultsState: MutableState<List<SearchResult.Place>>,
) {
    val isLocationPermissionGranted: Boolean
        get() = location.permission is LocationPermission.Granted

    val userLocation: LatLng?
        get() = location.lastLocation?.position?.toLatLng()

    val hasLocationFix: Boolean
        get() = location.lastLocation != null

    /** The highlighted place, or null for none. */
    var marker: LatLng? by markerState

    var lines: List<MapLine> by linesState

    var onLongPress: ((LatLng) -> Unit)? by mutableStateOf(null)

    /** Stops drawn as tappable dots. */
    var stops: List<SearchResult.Stop> by stopsState

    var onStopClick: ((SearchResult.Stop) -> Unit)? by mutableStateOf(null)

    /** Places tapped on the map or among [results]. */
    var onPlaceClick: ((SearchResult.Place) -> Unit)? by mutableStateOf(null)

    /** Search results drawn as pins. */
    var results: List<SearchResult.Place> by resultsState

    val center: LatLng
        get() = mapState.cameraPosition.center.toLatLng()

    val isMoving: Boolean
        get() = mapState.isCameraMoving

    val zoom: Double
        get() = mapState.cameraPosition.zoom

    /** Null until the map has been laid out. */
    val visibleBounds: GeoBounds?
        get() =
            mapState.viewport?.visibleBounds?.let {
                GeoBounds(it.southwest.latitude, it.southwest.longitude, it.northeast.latitude, it.northeast.longitude)
            }

    fun requestLocationPermission() = location.requestPermission()

    /** Returns false when there is no location fix yet. */
    suspend fun centerOnUser(): Boolean {
        val position = location.lastLocation?.position ?: return false
        mapState.animateCamera(
            CameraUpdate(center = position, zoom = maxOf(mapState.cameraPosition.zoom, USER_ZOOM)),
        )
        return true
    }

    /** Centers on [target]. With [zoomIn] the camera also zooms to street level unless it is already closer. */
    suspend fun show(
        target: LatLng,
        zoomIn: Boolean = true,
    ) {
        val current = mapState.cameraPosition.zoom
        mapState.animateCamera(CameraUpdate(center = target.toPosition(), zoom = if (zoomIn) maxOf(current, PLACE_ZOOM) else current))
    }

    suspend fun fit(points: List<LatLng>) {
        if (points.isEmpty()) return
        val box =
            BoundingBox(
                west = points.minOf { it.longitude },
                south = points.minOf { it.latitude },
                east = points.maxOf { it.longitude },
                north = points.maxOf { it.latitude },
            )
        mapState.animateCameraToBounds(box, CameraFit(fitPadding = DpPadding(FIT_MARGIN, FIT_MARGIN, FIT_MARGIN, FIT_MARGIN)))
    }
}

/** [loadStyle] downloads a style document by URL, used to build the dark style from the light one. */
@Composable
@UiComposable
fun rememberMapController(
    loadStyle: suspend (String) -> String,
    dark: Boolean = isSystemInDarkTheme(),
): MapController {
    val accent = MaterialTheme.colorScheme.primary
    val tint = remember(accent) { MapTint.of(accent.red, accent.green, accent.blue) }
    val darkBase by produceState<BaseStyle?>(null, dark, tint) {
        if (!dark) return@produceState
        value = runCatching { BaseStyle.Json(darkStyle(loadStyle(LIGHT_STYLE), tint)) }.getOrElse { BaseStyle.Uri(DARK_STYLE) }
    }
    val location = rememberLocationState()
    val markerColor = MaterialTheme.colorScheme.primary
    val casingColor = if (dark) Color.Black else Color.White
    val markerState = remember { mutableStateOf<LatLng?>(null, referentialEqualityPolicy()) }
    val linesState = remember { mutableStateOf<List<MapLine>>(emptyList(), referentialEqualityPolicy()) }
    val stopsState = remember { mutableStateOf<List<SearchResult.Stop>>(emptyList()) }
    val resultsState = remember { mutableStateOf<List<SearchResult.Place>>(emptyList()) }
    val controllerRef = remember { arrayOfNulls<MapController>(1) }
    val stopIcons =
        StopIcon.entries.associateWith { icon ->
            image(rememberVectorPainter(icon.vector), size = DpSize(14.dp, 14.dp), colorFilter = ColorFilter.tint(StopIconTint))
        }
    val mapState =
        rememberMapState(
            baseStyle = if (dark) darkBase ?: LoadingDarkStyle else BaseStyle.Uri(LIGHT_STYLE),
            initialCameraPosition = CameraPosition(center = EUROPE, zoom = 3.5),
        ) {
            PlaceLayers(dark) { place -> controllerRef[0]?.onPlaceClick?.invoke(place) }
            ResultLayers(resultsState.value, dark) { place -> controllerRef[0]?.onPlaceClick?.invoke(place) }
            linesState.value.forEachIndexed { index, line ->
                key(index, line) {
                    // Old and new layers coexist briefly while the list changes, so ids must not repeat across contents.
                    val id = "$index-${line.hashCode()}"
                    val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(line.points.map { it.toPosition() })))
                    LineLayer(
                        id = "line-casing-$id",
                        source = source,
                        color = const(casingColor),
                        width = const(line.width + 3.dp),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                    )
                    LineLayer(
                        id = "line-$id",
                        source = source,
                        color = const(line.color),
                        width = const(line.width),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                    )
                }
            }
            val stops = stopsState.value
            if (stops.isNotEmpty()) {
                val features =
                    stops.mapIndexed { index, stop ->
                        Feature(
                            Point(stop.location.toPosition()),
                            buildJsonObject {
                                put(STOP_INDEX, index)
                                put(STOP_ICON, StopIcon.of(stop.modes).name)
                            },
                        )
                    }
                val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
                val onClick: FeaturesClickHandler = { clicked ->
                    val index = clicked.firstOrNull()?.properties?.get(STOP_INDEX)?.jsonPrimitive?.intOrNull
                    val stop = index?.let(stops::getOrNull)
                    val handler = controllerRef[0]?.onStopClick
                    if (stop == null || handler == null) {
                        ClickResult.Pass
                    } else {
                        handler(stop)
                        ClickResult.Consume
                    }
                }
                CircleLayer(
                    id = "stops",
                    source = source,
                    minZoom = STOPS_MIN_ZOOM.toFloat(),
                    color = const(Color.White),
                    radius = const(10.dp),
                    strokeColor = const(markerColor),
                    strokeWidth = const(2.dp),
                    onClick = onClick,
                )
                SymbolLayer(
                    id = "stop-icons",
                    source = source,
                    minZoom = STOPS_MIN_ZOOM.toFloat(),
                    iconImage =
                        switch(
                            feature[STOP_ICON],
                            cases = StopIcon.entries.map { case(it.name, stopIcons.getValue(it)) },
                            fallback = stopIcons.getValue(StopIcon.BUS),
                        ),
                    iconAllowOverlap = const(true),
                    onClick = onClick,
                )
            }
            LocationIndicatorLayer(id = "user-location", locationState = location)
            markerState.value?.let { marker ->
                val source = rememberGeoJsonSource(GeoJsonData.Features(Point(marker.toPosition())))
                CircleLayer(
                    id = "marker",
                    source = source,
                    color = const(markerColor),
                    radius = const(9.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(3.dp),
                )
            }
        }
    return remember(mapState, location) { MapController(mapState, location, markerState, linesState, stopsState, resultsState) }
        .also { controllerRef[0] = it }
}

internal fun Position.toLatLng() = LatLng(latitude = latitude, longitude = longitude)

internal fun LatLng.toPosition() = Position(longitude = longitude, latitude = latitude)

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private val LoadingDarkStyle =
    BaseStyle.Json(
        """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#1c1c1c"}}]}""",
    )
private const val USER_ZOOM = 15.0
private const val PLACE_ZOOM = 16.0
private const val STOP_INDEX = "i"
private const val STOP_ICON = "m"
private val StopIconTint = Color(0xFF303030)

/** Below this zoom there are too many stops in view to load or draw. */
const val STOPS_MIN_ZOOM = 14.0
private val FIT_MARGIN = 48.dp
private val EUROPE = Position(longitude = 10.0, latitude = 50.0)

/** The icon drawn inside a stop dot, chosen by the most significant mode serving the stop. */
private enum class StopIcon(
    val vector: ImageVector,
    val modes: Set<TransportMode>,
) {
    RAIL(
        Icons.Default.Train,
        setOf(
            TransportMode.HIGHSPEED_RAIL,
            TransportMode.LONG_DISTANCE_RAIL,
            TransportMode.NIGHT_RAIL,
            TransportMode.REGIONAL_RAIL,
            TransportMode.SUBURBAN,
        ),
    ),
    SUBWAY(Icons.Default.DirectionsSubway, setOf(TransportMode.SUBWAY)),
    TRAM(Icons.Default.Tram, setOf(TransportMode.TRAM, TransportMode.CABLE_CAR, TransportMode.FUNICULAR)),
    FERRY(Icons.Default.DirectionsBoat, setOf(TransportMode.FERRY)),
    BUS(Icons.Default.DirectionsBus, emptySet()),
    ;

    companion object {
        fun of(modes: Set<TransportMode>): StopIcon = entries.firstOrNull { icon -> icon.modes.any { it in modes } } ?: BUS
    }
}
