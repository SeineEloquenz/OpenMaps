package nz.eloque.openmaps.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** Map camera, user location and the highlighted marker, shared between [OpenMap] and the UI around it. */
@Stable
class MapController internal constructor(
    internal val mapState: MapState,
    internal val location: LocationState,
    private val markerState: MutableState<LatLng?>,
) {
    val isLocationPermissionGranted: Boolean
        get() = location.permission is LocationPermission.Granted

    val userLocation: LatLng?
        get() = location.lastLocation?.position?.toLatLng()

    val hasLocationFix: Boolean
        get() = location.lastLocation != null

    /** The highlighted place, or null for none. */
    var marker: LatLng? by markerState

    fun requestLocationPermission() = location.requestPermission()

    /** Returns false when there is no location fix yet. */
    suspend fun centerOnUser(): Boolean {
        val position = location.lastLocation?.position ?: return false
        mapState.animateCamera(
            CameraUpdate(center = position, zoom = maxOf(mapState.cameraPosition.zoom, USER_ZOOM)),
        )
        return true
    }

    suspend fun show(
        target: LatLng,
        zoom: Double = PLACE_ZOOM,
    ) {
        mapState.animateCamera(CameraUpdate(center = target.toPosition(), zoom = zoom))
    }
}

@Composable
@UiComposable
fun rememberMapController(dark: Boolean = isSystemInDarkTheme()): MapController {
    val location = rememberLocationState()
    val markerColor = MaterialTheme.colorScheme.primary
    val markerState = remember { mutableStateOf<LatLng?>(null) }
    val mapState =
        rememberMapState(
            baseStyle = BaseStyle.Uri(if (dark) DARK_STYLE else LIGHT_STYLE),
            initialCameraPosition = CameraPosition(center = EUROPE, zoom = 3.5),
        ) {
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
    return remember(mapState, location) { MapController(mapState, location, markerState) }
}

internal fun Position.toLatLng() = LatLng(latitude = latitude, longitude = longitude)

internal fun LatLng.toPosition() = Position(longitude = longitude, latitude = latitude)

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val USER_ZOOM = 15.0
private const val PLACE_ZOOM = 16.0
private val EUROPE = Position(longitude = 10.0, latitude = 50.0)
