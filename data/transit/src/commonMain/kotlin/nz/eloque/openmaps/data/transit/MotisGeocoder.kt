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
    private val baseUrl: String,
    private val language: () -> String?,
) : Geocoder {
    override suspend fun search(
        query: String,
        near: LatLng?,
        stopsFirst: Boolean,
    ): List<SearchResult> {
        val matches: List<MatchDto> =
            client
                .get("$baseUrl/v1/geocode") {
                    parameter("text", query)
                    near?.let { parameter("place", it.asParameter()) }
                    language()?.let { parameter("language", it) }
                }.body()
        val results = matches.map { it.toSearchResult() }
        return if (stopsFirst) results.sortedByDescending { it is SearchResult.Stop } else results
    }

    override suspend fun reverse(location: LatLng): SearchResult? {
        val matches: List<MatchDto> =
            client
                .get("$baseUrl/v1/reverse-geocode") {
                    parameter("place", location.asParameter())
                    parameter("numResults", 1)
                }.body()
        return matches.firstOrNull()?.toSearchResult()
    }
}
