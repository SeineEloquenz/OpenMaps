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
        val id: PlaceId?,
        override val name: String,
        override val location: LatLng,
        override val area: String?,
        /** What kind of place it is, as lower-case English words joined by underscores, such as `charging_station`. */
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
    /** In the OpenStreetMap `opening_hours` syntax, which [OpeningHours] reads. */
    val openingHours: String?,
    val website: String?,
    val phone: String?,
    val wheelchair: Accessibility?,
    val email: String? = null,
    val operator: String? = null,
    /** The chain the place belongs to, such as `Aldi` or `Aral`. */
    val brand: String? = null,
    val description: String? = null,
    /** Lower-case English words joined by underscores, such as `italian` or `coffee_shop`. */
    val cuisine: List<String> = emptyList(),
    val features: Set<PlaceFeature> = emptySet(),
    /** True when using the place costs nothing, false when it costs, null when unknown. */
    val free: Boolean? = null,
    /** Hotel stars. */
    val stars: Int? = null,
    val fuels: Set<Fuel> = emptySet(),
    val chargers: List<Charger> = emptyList(),
    val links: MediaLinks = MediaLinks(),
    /** The area the place covers, for buildings and parks. */
    val bounds: GeoBounds? = null,
)

enum class Accessibility { YES, LIMITED, NO }

enum class PlaceFeature {
    EMERGENCY,
    TAKEAWAY,
    DELIVERY,
    OUTDOOR_SEATING,
    WIFI,
    VEGETARIAN,
    VEGAN,
    GLUTEN_FREE,
    CARDS,
    TOILETS,
    CASH_DEPOSIT,
    CHANGING_TABLE,
    BOTTLE_REFILL,
}

enum class Fuel { DIESEL, PETROL_95, E10, PETROL_98, LPG, CNG }

enum class Connector { CCS, TYPE_2, CHADEMO, CCS1, TYPE_1, TESLA, SCHUKO, CEE }

/** Charge points of one connector type at a station. */
data class Charger(
    val connector: Connector,
    val powerKw: Int?,
    val count: Int?,
)

/** The place's entries in Wikidata, Wikipedia and Wikimedia Commons, and an image of it. */
data class MediaLinks(
    val wikidata: String? = null,
    val wikipedia: String? = null,
    val commons: String? = null,
    val image: String? = null,
)
