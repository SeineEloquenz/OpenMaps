package nz.eloque.openmaps.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.UiComposable
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Map camera and user location, shared between [OpenMap] and the UI around it. */
@Stable
class MapController internal constructor(
    internal val mapState: MapState,
    internal val location: LocationState,
) {
    val isLocationPermissionGranted: Boolean
        get() = location.permission is LocationPermission.Granted

    val hasLocationFix: Boolean
        get() = location.lastLocation != null

    fun requestLocationPermission() = location.requestPermission()

    /** Returns false when there is no location fix yet. */
    suspend fun centerOnUser(): Boolean {
        val position = location.lastLocation?.position ?: return false
        mapState.animateCamera(
            CameraUpdate(center = position, zoom = maxOf(mapState.cameraPosition.zoom, USER_ZOOM)),
        )
        return true
    }
}

@Composable
@UiComposable
fun rememberMapController(dark: Boolean = isSystemInDarkTheme()): MapController {
    val location = rememberLocationState()
    val mapState =
        rememberMapState(
            baseStyle = BaseStyle.Uri(if (dark) DARK_STYLE else LIGHT_STYLE),
            initialCameraPosition = CameraPosition(center = EUROPE, zoom = 3.5),
        ) {
            LocationIndicatorLayer(id = "user-location", locationState = location)
        }
    return MapController(mapState, location)
}

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val USER_ZOOM = 15.0
private val EUROPE = Position(longitude = 10.0, latitude = 50.0)
