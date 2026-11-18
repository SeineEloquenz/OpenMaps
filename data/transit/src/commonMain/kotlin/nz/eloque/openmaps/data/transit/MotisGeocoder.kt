package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SearchResult

internal class MotisGeocoder(
    private val client: HttpClient,
    private val baseUrl: () -> String,
    private val language: () -> String?,
) : Geocoder {
    override suspend fun search(
        query: String,
        near: LatLng?,
        stopsFirst: Boolean,
    ): List<SearchResult> {
        val matches: List<MatchDto> =
            client
                .get("${baseUrl()}/v1/geocode") {
                    parameter("text", query)
                    near?.let {
                        parameter("place", it.asParameter())
                        parameter("placeBias", PLACE_BIAS)
                    }
                    language()?.let { parameter("language", it) }
                }.body()
        val results = matches.map { it.toSearchResult() }
        return if (stopsFirst) results.sortedByDescending { it is SearchResult.Stop } else results
    }

    override suspend fun reverse(location: LatLng): SearchResult? {
        val matches: List<MatchDto> =
            client
                .get("${baseUrl()}/v1/reverse-geocode") {
                    parameter("place", location.asParameter())
                    parameter("numResults", 1)
                    parameter("type", "ADDRESS")
                }.body()
        return matches.firstOrNull()?.toSearchResult()
    }
}

// MOTIS defaults to 1, which ranks a famous place 5000 km away above the same name round the corner. Above 3, nearby
// namesakes start to push out the cities people mean, such as a local "La Paris" over Paris.
private const val PLACE_BIAS = 3
