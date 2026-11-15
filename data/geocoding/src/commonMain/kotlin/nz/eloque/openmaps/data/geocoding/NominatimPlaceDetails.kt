package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.MediaLinks
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PlaceFeature

@Serializable
internal data class LookupResultDto(
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    val extratags: Map<String, String>? = null,
    val address: Map<String, String>? = null,
    val boundingbox: List<String>? = null,
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
        return results.firstOrNull()?.toDetails()
    }

    override suspend fun detailsOf(osmIds: List<OsmId>): Map<OsmId, PlaceDetails> =
        osmIds.distinct().chunked(MAX_LOOKUP_IDS).flatMap { chunk ->
            val results: List<LookupResultDto> =
                client
                    .get("${baseUrl()}/lookup") {
                        parameter("osm_ids", chunk.joinToString(",") { it.asLookupId() })
                        parameter("format", "jsonv2")
                        parameter("extratags", 1)
                        parameter("addressdetails", 1)
                    }.body()
            results.mapNotNull { result -> result.osmIdOrNull()?.let { it to result.toDetails() } }
        }.toMap()
}

private const val MAX_LOOKUP_IDS = 50

private fun LookupResultDto.osmIdOrNull(): OsmId? {
    val type =
        when (osmType) {
            "node" -> OsmType.NODE
            "way" -> OsmType.WAY
            "relation" -> OsmType.RELATION
            else -> return null
        }
    return osmId?.let { OsmId(type, it) }
}

private fun LookupResultDto.toDetails(): PlaceDetails {
    val tags = extratags.orEmpty()

    fun first(vararg keys: String) = keys.firstNotNullOfOrNull { tags[it] }?.substringBefore(';')?.trim()
    return PlaceDetails(
        address = address?.let(::formatAddress),
        openingHours = tags["opening_hours"],
        website = first("website", "contact:website"),
        phone = first("phone", "contact:phone"),
        wheelchair = tags["wheelchair"],
        email = first("email", "contact:email"),
        operator = tags["operator"],
        description = tags["description"],
        cuisine = tags["cuisine"]?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        features = features(tags),
        links = MediaLinks(tags["wikidata"], tags["wikipedia"], tags["wikimedia_commons"], tags["image"]),
        bounds = boundingbox?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 4 }?.let { GeoBounds(it[0], it[2], it[1], it[3]) },
        tags = tags,
    )
}

private val Yes = setOf("yes", "only", "designated")

private fun features(tags: Map<String, String>): Set<PlaceFeature> {
    fun yes(vararg keys: String) = keys.any { tags[it] in Yes }
    return buildSet {
        if (yes("emergency")) add(PlaceFeature.EMERGENCY)
        if (yes("takeaway")) add(PlaceFeature.TAKEAWAY)
        if (yes("delivery")) add(PlaceFeature.DELIVERY)
        if (yes("outdoor_seating")) add(PlaceFeature.OUTDOOR_SEATING)
        if (tags["internet_access"] in setOf("wlan", "wifi", "yes")) add(PlaceFeature.WIFI)
        if (yes("diet:vegetarian")) add(PlaceFeature.VEGETARIAN)
        if (yes("diet:vegan")) add(PlaceFeature.VEGAN)
        if (yes("diet:gluten_free")) add(PlaceFeature.GLUTEN_FREE)
        if (yes("payment:cards", "payment:credit_cards", "payment:debit_cards")) add(PlaceFeature.CARDS)
        if (yes("toilets")) add(PlaceFeature.TOILETS)
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
