package nz.eloque.openmaps.core.model

/** Kinds of places offered as quick searches, each matching OSM tags written as `key.value`. */
enum class PlaceCategory(
    val tags: List<String>,
) {
    RESTAURANTS(listOf("amenity.restaurant", "amenity.fast_food")),
    CAFES(listOf("amenity.cafe")),
    SUPERMARKETS(listOf("shop.supermarket", "shop.convenience")),
    GAS_STATIONS(listOf("amenity.fuel")),
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

interface CategorySearch {
    /** Places of [category] inside [bounds], nearest to [center] first. */
    suspend fun search(
        category: PlaceCategory,
        center: LatLng,
        bounds: GeoBounds,
    ): List<SearchResult.Place>
}
