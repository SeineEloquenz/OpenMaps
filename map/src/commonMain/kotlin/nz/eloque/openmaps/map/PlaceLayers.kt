package nz.eloque.openmaps.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.SearchResult
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.coalesce
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.gte
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.lt
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.VectorTileSource
import org.maplibre.compose.sources.getBaseSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/** Colour families for places, after the groups people know from other maps. */
private enum class PlaceGroup(
    val light: Color,
    val dark: Color,
) {
    FOOD(Color(0xFFE8710A), Color(0xFFFFA65C)),
    SHOPPING(Color(0xFF1A73E8), Color(0xFF8AB4F8)),
    HEALTH(Color(0xFFD93025), Color(0xFFF28B82)),
    LODGING(Color(0xFFC2185B), Color(0xFFF48FB1)),
    SIGHTS(Color(0xFF00897B), Color(0xFF4DD0C1)),
    NATURE(Color(0xFF188038), Color(0xFF81C995)),
    SERVICES(Color(0xFF5F6B7A), Color(0xFFAEB8C4)),
}

/** OpenMapTiles `poi` classes and OSM tag values with their icon. Tile classes not listed here, such as benches or bollards, stay hidden. */
private enum class PlaceIcon(
    val vector: ImageVector,
    val group: PlaceGroup,
    val classes: List<String>,
) {
    RESTAURANT(Icons.Default.Restaurant, PlaceGroup.FOOD, listOf("restaurant")),
    FAST_FOOD(Icons.Default.Fastfood, PlaceGroup.FOOD, listOf("fast_food")),
    CAFE(Icons.Default.LocalCafe, PlaceGroup.FOOD, listOf("cafe")),
    BAR(Icons.Default.LocalBar, PlaceGroup.FOOD, listOf("bar")),
    PUB(Icons.Default.SportsBar, PlaceGroup.FOOD, listOf("beer", "pub", "biergarten")),
    ICE_CREAM(Icons.Default.Icecream, PlaceGroup.FOOD, listOf("ice_cream")),
    BAKERY(Icons.Default.BakeryDining, PlaceGroup.FOOD, listOf("bakery", "pastry")),
    GROCERY(Icons.Default.LocalGroceryStore, PlaceGroup.SHOPPING, listOf("grocery", "butcher", "supermarket", "convenience")),
    CLOTHES(Icons.Default.Checkroom, PlaceGroup.SHOPPING, listOf("clothing_store")),
    LIQUOR(Icons.Default.Liquor, PlaceGroup.SHOPPING, listOf("alcohol_shop")),
    BICYCLE(Icons.AutoMirrored.Filled.DirectionsBike, PlaceGroup.SHOPPING, listOf("bicycle")),
    CAR(Icons.Default.DirectionsCar, PlaceGroup.SHOPPING, listOf("car")),
    HAIRDRESSER(Icons.Default.ContentCut, PlaceGroup.SHOPPING, listOf("hairdresser")),
    LAUNDRY(Icons.Default.LocalLaundryService, PlaceGroup.SHOPPING, listOf("laundry")),
    MUSIC(Icons.Default.MusicNote, PlaceGroup.SHOPPING, listOf("music")),
    SHOP(Icons.Default.ShoppingBag, PlaceGroup.SHOPPING, listOf("shop", "jewelry", "florist", "furniture")),
    PHARMACY(Icons.Default.LocalPharmacy, PlaceGroup.HEALTH, listOf("pharmacy")),
    HOSPITAL(Icons.Default.LocalHospital, PlaceGroup.HEALTH, listOf("hospital")),
    DOCTOR(Icons.Default.MedicalServices, PlaceGroup.HEALTH, listOf("doctors", "dentist")),
    VET(Icons.Default.Pets, PlaceGroup.HEALTH, listOf("veterinary")),
    HOTEL(Icons.Default.Hotel, PlaceGroup.LODGING, listOf("lodging", "hotel", "hostel", "guest_house", "motel")),
    CAMPSITE(Icons.Default.Cabin, PlaceGroup.LODGING, listOf("campsite")),
    ATTRACTION(Icons.Default.PhotoCamera, PlaceGroup.SIGHTS, listOf("attraction", "monument", "viewpoint", "theme_park")),
    MUSEUM(Icons.Default.Museum, PlaceGroup.SIGHTS, listOf("museum")),
    GALLERY(Icons.Default.Palette, PlaceGroup.SIGHTS, listOf("art_gallery", "gallery")),
    CASTLE(Icons.Default.Castle, PlaceGroup.SIGHTS, listOf("castle")),
    THEATRE(Icons.Default.TheaterComedy, PlaceGroup.SIGHTS, listOf("theatre")),
    CINEMA(Icons.Default.Movie, PlaceGroup.SIGHTS, listOf("cinema")),
    ZOO(Icons.Default.Pets, PlaceGroup.NATURE, listOf("zoo", "dog_park")),
    PARK(Icons.Default.Park, PlaceGroup.NATURE, listOf("park", "garden", "picnic_site")),
    SWIMMING(Icons.Default.Pool, PlaceGroup.NATURE, listOf("swimming_pool", "swimming", "water_park")),
    SPORTS(Icons.Default.FitnessCenter, PlaceGroup.NATURE, listOf("sports_centre", "stadium")),
    FUEL(Icons.Default.LocalGasStation, PlaceGroup.SERVICES, listOf("fuel")),
    CHARGING(Icons.Default.EvStation, PlaceGroup.SERVICES, listOf("charging_station")),
    ATM(Icons.Default.LocalAtm, PlaceGroup.SERVICES, listOf("atm")),
    BANK(Icons.Default.AccountBalance, PlaceGroup.SERVICES, listOf("bank", "town_hall")),
    POST(Icons.Default.LocalPostOffice, PlaceGroup.SERVICES, listOf("post")),
    TOILETS(Icons.Default.Wc, PlaceGroup.SERVICES, listOf("toilets")),
    DRINKING_WATER(Icons.Default.WaterDrop, PlaceGroup.SERVICES, listOf("drinking_water")),
    POLICE(Icons.Default.LocalPolice, PlaceGroup.SERVICES, listOf("police")),
    LIBRARY(Icons.Default.LocalLibrary, PlaceGroup.SERVICES, listOf("library")),
    SCHOOL(Icons.Default.School, PlaceGroup.SERVICES, listOf("school", "college")),
    WORSHIP(Icons.Default.Church, PlaceGroup.SERVICES, listOf("place_of_worship")),
    ;

    companion object {
        fun of(category: String?): PlaceIcon? = entries.firstOrNull { category in it.classes }
    }
}

/** A filled circle in [color] with [icon] drawn on it in white. */
internal class BadgePainter(
    private val icon: Painter,
    private val color: Color,
    private val outline: Color,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawCircle(color)
        drawCircle(outline, style = Stroke(width = size.minDimension / 14))
        inset(size.minDimension * 0.2f) {
            with(icon) { draw(size, colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}

/**
 * Places of interest from the base map's tiles, drawn the same in light and dark styles.
 * More of them appear as the map zooms in, the most important first.
 */
@Composable
internal fun PlaceLayers(
    dark: Boolean,
    onClick: (SearchResult.Place) -> Unit,
) {
    val tiles = getBaseSource<VectorTileSource>(BASE_SOURCE) ?: return
    val outline = if (dark) Color.Black else Color.White
    val badges =
        PlaceIcon.entries.associateWith { icon ->
            val painter = rememberVectorPainter(icon.vector)
            val color = if (dark) icon.group.dark else icon.group.light
            image(remember(painter, color, outline) { BadgePainter(painter, color, outline) }, size = DpSize(20.dp, 20.dp))
        }
    val placeClass = feature["class"].asString()
    val iconImage =
        switch(
            placeClass,
            cases = PlaceIcon.entries.map { case(it.classes, badges.getValue(it)) },
            fallback = badges.getValue(PlaceIcon.SHOP),
        )
    val textColor =
        switch(
            placeClass,
            cases = PlaceIcon.entries.map { case(it.classes, const(if (dark) it.group.dark else it.group.light)) },
            fallback = const(if (dark) PlaceGroup.SHOPPING.dark else PlaceGroup.SHOPPING.light),
        )
    val shown = switch(placeClass, cases = listOf(case(PlaceIcon.entries.flatMap { it.classes }, const(true))), fallback = const(false))
    val rank = feature["rank"].asNumber(const(RANK_ALL))
    val handler: FeaturesClickHandler = { clicked ->
        val place = clicked.firstOrNull()?.toPlace(position?.toLatLng())
        if (place == null) {
            ClickResult.Pass
        } else {
            onClick(place)
            ClickResult.Consume
        }
    }
    // Higher layers win label collisions, so the most important tier is added last.
    // A symbol sort key would order them within a tier, but drops panning to about 15 fps once the last tier shows.
    RankTiers.indices.reversed().forEach { index ->
        val (minZoom, below) = RankTiers[index]
        val above = RankTiers.getOrNull(index - 1)?.second ?: 0
        SymbolLayer(
            id = "places-$index",
            source = tiles,
            sourceLayer = "poi",
            minZoom = minZoom,
            filter = all(shown, rank gte const(above.toFloat()), rank lt const(below.toFloat())),
            iconImage = iconImage,
            textField = coalesce(feature["name"].asString(), const("")).cast(),
            textFont = const(listOf("Noto Sans Regular")),
            textSize = const(0.8f.em),
            textColor = textColor,
            textHaloColor = const(if (dark) Color(0xFF121212) else Color.White),
            textHaloWidth = const(1.5.dp),
            textAnchor = const(SymbolAnchor.Left),
            textOffset = textOffset(0.9f.em, 0f.em),
            textMaxWidth = const(8f.em),
            onClick = handler,
        )
    }
}

/** The place a `poi` tile feature stands for. Unnamed places get an empty name unless [named] is required. */
internal fun Feature<*, JsonObject?>.toPlace(
    tapped: LatLng?,
    named: Boolean = true,
): SearchResult.Place? {
    val properties = properties ?: return null
    val name = properties["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: if (named) return null else ""
    val location = (geometry as? Point)?.coordinates?.toLatLng() ?: tapped ?: return null
    val encoded = id?.content?.toDoubleOrNull()?.toLong()
    // OpenMapTiles encodes the OSM type in the last digit of the feature id.
    val type =
        when (encoded?.rem(10)) {
            2L -> "way"
            3L -> "relation"
            else -> "node"
        }
    val category = properties["subclass"]?.jsonPrimitive?.content ?: properties["class"]?.jsonPrimitive?.content
    return SearchResult.Place(encoded?.let { PlaceId("osm:$type/${it / 10}") }, name, location, area = null, category = category)
}

/** Base style layers that draw places themselves, hidden in favour of [PlaceLayers]. */
internal val BaseStylePlaceLayers = listOf("poi_r1", "poi_r7", "poi_r20")

internal const val BASE_SOURCE = "openmaptiles"

/** OpenMapTiles `poi` class or subclass values of the places in a category. */
internal val PlaceCategory.tileValues: Set<String>
    get() =
        when (this) {
            PlaceCategory.RESTAURANTS -> setOf("restaurant", "fast_food")
            PlaceCategory.CAFES -> setOf("cafe")
            PlaceCategory.SUPERMARKETS -> setOf("supermarket", "convenience")
            PlaceCategory.GAS_STATIONS -> setOf("fuel")
            PlaceCategory.CHARGING -> setOf("charging_station")
            PlaceCategory.BARS -> setOf("bar", "pub", "biergarten")
            PlaceCategory.ATTRACTIONS -> setOf("attraction", "museum", "gallery", "viewpoint", "zoo", "theme_park", "castle", "monument")
            PlaceCategory.HOTELS -> setOf("hotel", "hostel", "guest_house", "motel")
            PlaceCategory.PHARMACIES -> setOf("pharmacy")
            PlaceCategory.BAKERIES -> setOf("bakery", "pastry")
            PlaceCategory.ATMS -> setOf("atm")
            PlaceCategory.TOILETS -> setOf("toilets")
            PlaceCategory.DRINKING_WATER -> setOf("drinking_water")
            PlaceCategory.SHARED_VEHICLES -> setOf("bicycle_rental", "car_sharing")
        }
private const val RANK_ALL = 1000f

/** Zoom at which each rank band appears, paired with the rank it ends below. Matches the light base style. */
private val RankTiers = listOf(15f to 7, 16f to 20, 17f to RANK_ALL.toInt())

/** Search results drawn as labelled pins above the regular places. */
@Composable
internal fun ResultLayers(
    results: List<SearchResult.Place>,
    dark: Boolean,
    onClick: (SearchResult.Place) -> Unit,
) {
    if (results.isEmpty()) return
    val color = if (dark) ResultColorDark else ResultColorLight
    val outline = if (dark) Color.Black else Color.White
    val badges =
        PlaceIcon.entries.associateWith { icon ->
            val painter = rememberVectorPainter(icon.vector)
            image(remember(painter, color, outline) { BadgePainter(painter, color, outline) }, size = DpSize(28.dp, 28.dp))
        }
    val features =
        results.mapIndexed { index, result ->
            Feature(
                Point(result.location.toPosition()),
                buildJsonObject {
                    put(RESULT_INDEX, index)
                    put(RESULT_NAME, result.name)
                    put(RESULT_ICON, (PlaceIcon.of(result.category) ?: PlaceIcon.SHOP).name)
                },
            )
        }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val handler: FeaturesClickHandler = { clicked ->
        val index = clicked.firstOrNull()?.properties?.get(RESULT_INDEX)?.jsonPrimitive?.intOrNull
        val result = index?.let(results::getOrNull)
        if (result == null) {
            ClickResult.Pass
        } else {
            onClick(result)
            ClickResult.Consume
        }
    }
    SymbolLayer(
        id = "results",
        source = source,
        iconImage =
            switch(
                feature[RESULT_ICON].asString(),
                cases = PlaceIcon.entries.map { case(it.name, badges.getValue(it)) },
                fallback = badges.getValue(PlaceIcon.SHOP),
            ),
        iconAllowOverlap = const(true),
        textField = coalesce(feature[RESULT_NAME].asString(), const("")).cast(),
        textFont = const(listOf("Noto Sans Regular")),
        textSize = const(0.85f.em),
        textColor = const(color),
        textHaloColor = const(if (dark) Color(0xFF121212) else Color.White),
        textHaloWidth = const(1.5.dp),
        textAnchor = const(SymbolAnchor.Left),
        textOffset = textOffset(1.3f.em, 0f.em),
        textMaxWidth = const(8f.em),
        textOptional = const(true),
        onClick = handler,
    )
}

private const val RESULT_INDEX = "i"
private const val RESULT_NAME = "n"
private const val RESULT_ICON = "c"
private val ResultColorLight = Color(0xFFD93025)
private val ResultColorDark = Color(0xFFF28B82)
