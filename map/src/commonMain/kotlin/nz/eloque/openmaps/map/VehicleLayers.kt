package nz.eloque.openmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/** A transit vehicle where it is right now, labelled with its line. */
data class VehicleMarker(
    val tripId: String,
    val name: String,
    val location: LatLng,
    val color: Color,
)

@Composable
internal fun VehicleLayers(
    vehicles: List<VehicleMarker>,
    dark: Boolean,
    onClick: (VehicleMarker) -> Unit,
) {
    if (vehicles.isEmpty()) return
    val features =
        vehicles.mapIndexed { index, vehicle ->
            Feature(
                Point(vehicle.location.toPosition()),
                buildJsonObject {
                    put(VEHICLE_INDEX, index)
                    put(VEHICLE_COLOR, vehicle.color.hex())
                    put(VEHICLE_TEXT, (if (vehicle.color.luminance() > 0.5f) Color.Black else Color.White).hex())
                    put(VEHICLE_NAME, vehicle.name)
                },
            )
        }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val handler: FeaturesClickHandler = { clicked ->
        val vehicle = clicked.firstOrNull()?.properties?.get(VEHICLE_INDEX)?.jsonPrimitive?.intOrNull?.let(vehicles::getOrNull)
        if (vehicle == null) {
            ClickResult.Pass
        } else {
            onClick(vehicle)
            ClickResult.Consume
        }
    }
    CircleLayer(
        id = "vehicles",
        source = source,
        color = feature[VEHICLE_COLOR].convertToColor(),
        radius = const(11.dp),
        strokeColor = const(if (dark) PinRingDark else Color.White),
        strokeWidth = const(1.5.dp),
        onClick = handler,
    )
    SymbolLayer(
        id = "vehicle-names",
        source = source,
        textField = feature[VEHICLE_NAME].asString().cast(),
        textFont = const(listOf("Noto Sans Bold")),
        textSize = const(0.7f.em),
        textColor = feature[VEHICLE_TEXT].convertToColor(),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        onClick = handler,
    )
}

private fun Color.hex() = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')

private const val VEHICLE_INDEX = "i"
private const val VEHICLE_COLOR = "c"
private const val VEHICLE_TEXT = "t"
private const val VEHICLE_NAME = "n"
