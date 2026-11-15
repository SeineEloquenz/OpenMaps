package nz.eloque.openmaps.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.VehicleKind
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/** A shared vehicle, or a station holding [count] of them, drawn with an icon for its [kind]. */
data class SharingPin(
    val id: String,
    val location: LatLng,
    val kind: VehicleKind,
    val electric: Boolean,
    val station: Boolean,
    val count: Int? = null,
)

/** Stations as larger badges with their vehicle count, freely parked vehicles as small ones that give way when crowded. */
@Composable
internal fun SharingLayers(
    pins: List<SharingPin>,
    dark: Boolean,
    onClick: (String) -> Unit,
) {
    if (pins.isEmpty()) return
    val color = if (dark) SharingColorDark else SharingColorLight
    val outline = if (dark) Color.Black else Color.White
    val handler: FeaturesClickHandler = { clicked ->
        val index = clicked.firstOrNull()?.properties?.get(PIN_INDEX)?.jsonPrimitive?.intOrNull
        val pin = index?.let(pins::getOrNull)
        if (pin == null) {
            ClickResult.Pass
        } else {
            onClick(pin.id)
            ClickResult.Consume
        }
    }
    listOf(false, true).forEach { stations ->
        val group = pins.withIndex().filter { it.value.station == stations }
        if (group.isEmpty()) return@forEach
        val size = if (stations) 26.dp else 18.dp
        val badges =
            SharingIcon.entries.associateWith { icon ->
                val painter = rememberVectorPainter(icon.vector)
                image(remember(painter, color, outline) { BadgePainter(painter, color, outline) }, size = DpSize(size, size))
            }
        val features =
            group.map { (index, pin) ->
                Feature(
                    Point(pin.location.toPosition()),
                    buildJsonObject {
                        put(PIN_INDEX, index)
                        put(PIN_ICON, SharingIcon.of(pin.kind, pin.electric).name)
                        pin.count?.let { put(PIN_COUNT, it.toString()) }
                    },
                )
            }
        val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
        SymbolLayer(
            id = if (stations) "sharing-stations" else "sharing-vehicles",
            source = source,
            iconImage =
                switch(
                    feature[PIN_ICON].asString(),
                    cases = SharingIcon.entries.map { case(it.name, badges.getValue(it)) },
                    fallback = badges.getValue(SharingIcon.BIKE),
                ),
            iconAllowOverlap = const(stations),
            textField = coalesce(feature[PIN_COUNT].asString(), const("")).cast(),
            textFont = const(listOf("Noto Sans Bold")),
            textSize = const(0.8f.em),
            textColor = const(color),
            textHaloColor = const(outline),
            textHaloWidth = const(1.5.dp),
            textAnchor = const(SymbolAnchor.Left),
            textOffset = textOffset(1.2f.em, 0f.em),
            textOptional = const(true),
            onClick = handler,
        )
    }
}

private enum class SharingIcon(
    val vector: ImageVector,
) {
    BIKE(Icons.AutoMirrored.Filled.DirectionsBike),
    E_BIKE(Icons.Default.ElectricBike),
    CARGO_BIKE(Icons.Default.PedalBike),
    SCOOTER(Icons.Default.ElectricScooter),
    MOPED(Icons.Default.Moped),
    CAR(Icons.Default.DirectionsCar),
    ;

    companion object {
        fun of(
            kind: VehicleKind,
            electric: Boolean,
        ) = when (kind) {
            VehicleKind.BICYCLE -> if (electric) E_BIKE else BIKE
            VehicleKind.CARGO_BICYCLE -> CARGO_BIKE
            VehicleKind.SCOOTER -> SCOOTER
            VehicleKind.MOPED -> MOPED
            VehicleKind.CAR -> CAR
            VehicleKind.OTHER -> BIKE
        }
    }
}

private const val PIN_INDEX = "i"
private const val PIN_ICON = "k"
private const val PIN_COUNT = "n"
private val SharingColorLight = Color(0xFF00897B)
private val SharingColorDark = Color(0xFF4DD0C1)
