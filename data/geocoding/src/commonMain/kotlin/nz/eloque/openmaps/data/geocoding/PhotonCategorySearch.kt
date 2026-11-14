package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.CategorySearch
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.SearchResult

@Serializable
internal data class PhotonResponseDto(
    val features: List<PhotonFeatureDto> = emptyList(),
)

@Serializable
internal data class PhotonFeatureDto(
    val geometry: PhotonGeometryDto,
    val properties: PhotonPropertiesDto,
)

@Serializable
internal data class PhotonGeometryDto(
    val coordinates: List<Double>,
)

@Serializable
internal data class PhotonPropertiesDto(
    val name: String? = null,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("osm_value") val osmValue: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
)

internal class PhotonCategorySearch(
    private val client: HttpClient,
    private val language: () -> String?,
    private val baseUrl: () -> String,
) : CategorySearch {
    override suspend fun search(
        category: PlaceCategory,
        center: LatLng,
        bounds: GeoBounds,
    ): List<SearchResult.Place> {
        val response: PhotonResponseDto =
            client
                .get("${baseUrl()}/api/") {
                    parameter("include", category.tags.joinToString(",") { "osm.$it" })
                    parameter("bbox", "${bounds.west},${bounds.south},${bounds.east},${bounds.north}")
                    parameter("lat", center.latitude)
                    parameter("lon", center.longitude)
                    parameter("limit", LIMIT)
                    language()?.takeIf { it in LANGUAGES }?.let { parameter("lang", it) }
                }.body()
        return response.features
            .mapNotNull { it.toPlace() }
            .sortedBy { it.location.distanceTo(center) }
    }
}

private fun PhotonFeatureDto.toPlace(): SearchResult.Place? {
    val name = properties.name.orEmpty()
    val (longitude, latitude) = geometry.coordinates.takeIf { it.size >= 2 } ?: return null
    val type =
        when (properties.osmType) {
            "N" -> OsmType.NODE
            "W" -> OsmType.WAY
            "R" -> OsmType.RELATION
            else -> null
        }
    val osmId = if (type != null && properties.osmId != null) OsmId(type, properties.osmId) else null
    val street = listOfNotNull(properties.street, properties.housenumber).joinToString(" ").takeIf { it.isNotBlank() }
    return SearchResult.Place(osmId, name, LatLng(latitude, longitude), street ?: properties.city, properties.osmValue)
}

private const val LIMIT = 30

/** The languages Photon has names for. Others fall back to local names. */
private val LANGUAGES = setOf("de", "en", "fr")
