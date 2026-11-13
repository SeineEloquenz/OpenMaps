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
    val openingHours: String?,
    val website: String?,
    val phone: String?,
    val wheelchair: String?,
)
