package nz.eloque.openmaps.core.model

/** Kinds of places offered as quick searches, each matching OSM tags written as `key.value`. */
enum class PlaceCategory(
    val tags: List<String>,
) {
    RESTAURANTS(listOf("amenity.restaurant", "amenity.fast_food")),
    CAFES(listOf("amenity.cafe")),
    SUPERMARKETS(listOf("shop.supermarket", "shop.convenience")),
    GAS_STATIONS(listOf("amenity.fuel")),
    CHARGING(listOf("amenity.charging_station")),
    BARS(listOf("amenity.bar", "amenity.pub", "amenity.biergarten")),
    ATTRACTIONS(
        listOf(
            "tourism.attraction",
            "tourism.museum",
            "tourism.gallery",
            "tourism.viewpoint",
            "tourism.zoo",
            "tourism.theme_park",
            "historic.castle",
            "historic.monument",
        ),
    ),
    HOTELS(listOf("tourism.hotel", "tourism.hostel", "tourism.guest_house", "tourism.motel")),
    PHARMACIES(listOf("amenity.pharmacy")),
    BAKERIES(listOf("shop.bakery", "shop.pastry")),
    ATMS(listOf("amenity.atm")),
    TOILETS(listOf("amenity.toilets")),
}

/** A charging station database with more detail than OpenStreetMap, used for [PlaceCategory.CHARGING] when set up. */
interface ChargingStations {
    /** False until the user has entered what the service needs, such as an API key. */
    val available: Boolean

    /** Stations inside [bounds], nearest to [center] first, with their details as OSM-style tags. */
    suspend fun stations(
        center: LatLng,
        bounds: GeoBounds,
    ): List<Pair<SearchResult.Place, PlaceDetails>>
}

interface CategorySearch {
    /** Places of [category] inside [bounds], nearest to [center] first. Unnamed places have an empty name. */
    suspend fun search(
        category: PlaceCategory,
        center: LatLng,
        bounds: GeoBounds,
    ): List<SearchResult.Place>
}
