package nz.eloque.openmaps.core.model

sealed interface SearchResult {
    val name: String
    val location: LatLng

    /** Locality shown under the name, such as the city. */
    val area: String?

    data class Stop(
        val id: String,
        override val name: String,
        override val location: LatLng,
        override val area: String?,
        val modes: Set<TransportMode>,
    ) : SearchResult

    data class Place(
        val osmId: OsmId?,
        override val name: String,
        override val location: LatLng,
        override val area: String?,
        val category: String?,
    ) : SearchResult

    data class Address(
        override val name: String,
        override val location: LatLng,
        override val area: String?,
        val street: String?,
        val houseNumber: String?,
        val postcode: String?,
    ) : SearchResult
}

data class PlaceDetails(
    /** Street address on one line, such as `Augustastraße 13, 76137 Karlsruhe`. */
    val address: String?,
    val openingHours: String?,
    val website: String?,
    val phone: String?,
    val wheelchair: String?,
    val email: String? = null,
    val operator: String? = null,
    val description: String? = null,
    /** OSM cuisine values such as `italian` or `coffee_shop`. */
    val cuisine: List<String> = emptyList(),
    val features: Set<PlaceFeature> = emptySet(),
    val links: MediaLinks = MediaLinks(),
    /** The area the place covers, for buildings and parks. */
    val bounds: GeoBounds? = null,
)

enum class PlaceFeature { EMERGENCY, TAKEAWAY, DELIVERY, OUTDOOR_SEATING, WIFI, VEGETARIAN, VEGAN, GLUTEN_FREE, CARDS, TOILETS }

/** The OSM `wikidata`, `wikipedia`, `wikimedia_commons` and `image` tags of a place. */
data class MediaLinks(
    val wikidata: String? = null,
    val wikipedia: String? = null,
    val commons: String? = null,
    val image: String? = null,
)
