package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource

@Serializable
internal data class LookupResultDto(
    val extratags: Map<String, String>? = null,
    val address: Map<String, String>? = null,
)

internal class NominatimPlaceDetails(
    private val client: HttpClient,
    private val baseUrl: () -> String,
) : PlaceDetailsSource {
    override suspend fun details(osmId: OsmId): PlaceDetails? {
        val results: List<LookupResultDto> =
            client
                .get("${baseUrl()}/lookup") {
                    parameter("osm_ids", osmId.asLookupId())
                    parameter("format", "jsonv2")
                    parameter("extratags", 1)
                    parameter("addressdetails", 1)
                }.body()
        val result = results.firstOrNull() ?: return null
        val tags = result.extratags.orEmpty()
        return PlaceDetails(
            address = result.address?.let(::formatAddress),
            openingHours = tags["opening_hours"],
            website = tags["website"] ?: tags["contact:website"],
            phone = tags["phone"] ?: tags["contact:phone"],
            wheelchair = tags["wheelchair"],
        )
    }
}

private fun formatAddress(address: Map<String, String>): String? {
    val street = listOfNotNull(address["road"] ?: address["pedestrian"], address["house_number"]).joinToString(" ")
    val locality = address["city"] ?: address["town"] ?: address["village"] ?: address["municipality"]
    val place = listOfNotNull(address["postcode"], locality).joinToString(" ")
    return listOf(street, place).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotEmpty() }
}

private fun OsmId.asLookupId(): String {
    val prefix =
        when (type) {
            OsmType.NODE -> "N"
            OsmType.WAY -> "W"
            OsmType.RELATION -> "R"
        }
    return "$prefix$id"
}
