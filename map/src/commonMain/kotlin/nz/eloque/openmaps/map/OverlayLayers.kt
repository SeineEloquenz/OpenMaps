package nz.eloque.openmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.MapLayer
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.exponential
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.VectorTileSource
import org.maplibre.compose.sources.getBaseSource
import org.maplibre.compose.sources.rememberRasterDemTileSource
import org.maplibre.compose.sources.rememberRasterTileSource

/** The optional [layers] that sit within the base map, between its land and its roads or labels. */
@Composable
internal fun OverlayLayers(
    layers: Set<MapLayer>,
    dark: Boolean,
) {
    if (MapLayer.TERRAIN in layers) {
        val dem = rememberRasterDemTileSource(TERRAIN_TILEJSON, tileSize = 512)
        Anchor.Below(FIRST_WATER_LAYER) {
            HillshadeLayer(
                id = "terrain",
                source = dem,
                shadowColor = const(if (dark) Color.Black else Color(0xFF473B24)),
                highlightColor = const(if (dark) Color(0xFF5C5C5C) else Color.White),
                exaggeration = const(TERRAIN_EXAGGERATION),
            )
        }
    }
    if (MapLayer.TRANSIT in layers) TransitLines(dark)
    Trail(MapLayer.CYCLING in layers, "cycling")
    Trail(MapLayer.HIKING in layers, "hiking")
    if (MapLayer.SEAMARKS in layers) Seamarks()
}

@Composable
private fun Seamarks() {
    val tiles =
        rememberRasterTileSource(listOf(SEAMARKS_TILES), tileSize = 256) {
            maxZoom = SEAMARKS_MAX_ZOOM
            attributionHtml = SEAMARKS_ATTRIBUTION
        }
    RasterLayer(id = "seamarks", source = tiles)
}

@Composable
private fun Trail(
    shown: Boolean,
    kind: String,
) {
    if (!shown) return
    val tiles =
        rememberRasterTileSource(
            listOf("https://tile.waymarkedtrails.org/$kind/{z}/{x}/{y}.png"),
            tileSize = 256,
        ) {
            maxZoom = TRAILS_MAX_ZOOM
            attributionHtml = TRAILS_ATTRIBUTION
        }
    Anchor.Below(FIRST_LABEL_LAYER) {
        RasterLayer(id = "trails-$kind", source = tiles, opacity = const(TRAILS_OPACITY))
    }
}

/** Railways, subways and trams of the base map's own tiles, drawn in strong colours. */
@Composable
private fun TransitLines(dark: Boolean) {
    val tiles = getBaseSource<VectorTileSource>(BASE_SOURCE) ?: return
    val subclass = feature["subclass"].asString()
    Anchor.Below(FIRST_LABEL_LAYER) {
        LineLayer(
            id = "transit-lines",
            source = tiles,
            sourceLayer = "transportation",
            filter =
                switch(
                    feature["class"].asString(),
                    case("rail", coalesce(feature["service"].asString(), const("")) eq const("")),
                    case("transit", const(true)),
                    fallback = const(false),
                ),
            color =
                switch(
                    subclass,
                    case("subway", const(if (dark) SubwayDark else Subway)),
                    case("light_rail", const(if (dark) TramDark else Tram)),
                    case("tram", const(if (dark) TramDark else Tram)),
                    fallback = const(if (dark) RailDark else Rail),
                ),
            width = interpolate(exponential(1.4f), zoom(), 8 to const(1.dp), 14 to const(3.dp), 18 to const(6.dp)),
            cap = const(LineCap.Round),
        )
    }
}

private const val TERRAIN_TILEJSON = "https://tiles.mapterhorn.com/tilejson.json"
private const val TERRAIN_EXAGGERATION = 0.8f
private const val FIRST_WATER_LAYER = "waterway_tunnel"

// The first label layer of the OpenFreeMap styles, so overlays stay under names and road numbers.
private const val FIRST_LABEL_LAYER = "road_one_way_arrow"
private const val TRAILS_MAX_ZOOM = 18
private const val TRAILS_OPACITY = 0.85f
private const val TRAILS_ATTRIBUTION =
    "<a href=\"https://waymarkedtrails.org\">Waymarked Trails</a> " +
        "<a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap contributors</a>"

private const val SEAMARKS_TILES = "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png"
private const val SEAMARKS_MAX_ZOOM = 18
private const val SEAMARKS_ATTRIBUTION =
    "<a href=\"https://www.openseamap.org\">© OpenSeaMap contributors</a>, " +
        "<a href=\"https://creativecommons.org/licenses/by-sa/2.0/\">CC BY-SA</a>"

private val Rail = Color(0xFF1565C0)
private val RailDark = Color(0xFF64B5F6)
private val Subway = Color(0xFF6A1B9A)
private val SubwayDark = Color(0xFFCE93D8)
private val Tram = Color(0xFFC62828)
private val TramDark = Color(0xFFEF9A9A)
