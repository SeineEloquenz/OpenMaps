package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import nz.eloque.openmaps.core.model.DeparturesResult
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanResult
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.TransitRepository
import kotlin.time.Instant

internal class MotisTransitRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : TransitRepository {
    override suspend fun plan(request: PlanRequest): PlanResult {
        val response: PlanResponseDto =
            client
                .get("$baseUrl/v6/plan") {
                    parameter("fromPlace", request.from.asParameter())
                    parameter("toPlace", request.to.asParameter())
                    request.time?.let { parameter("time", it.toString()) }
                    parameter("arriveBy", request.arriveBy)
                    parameter("directModes", "WALK")
                    request.pageCursor?.let { parameter("pageCursor", it) }
                }.body()
        return PlanResult(
            itineraries = response.itineraries.map { it.toItinerary() },
            direct = response.direct.map { it.toItinerary() },
            earlierCursor = response.previousPageCursor,
            laterCursor = response.nextPageCursor,
        )
    }

    override suspend fun departures(
        stopId: String,
        time: Instant?,
        count: Int,
        pageCursor: String?,
    ): DeparturesResult =
        stopTimes {
            parameter("stopId", stopId)
            time?.let { parameter("time", it.toString()) }
            parameter("n", count)
            pageCursor?.let { parameter("pageCursor", it) }
        }

    override suspend fun departuresNear(
        location: LatLng,
        radiusMeters: Int,
        count: Int,
    ): DeparturesResult =
        stopTimes {
            parameter("center", location.asParameter())
            parameter("radius", radiusMeters)
            parameter("n", count)
        }

    private suspend fun stopTimes(block: HttpRequestBuilder.() -> Unit): DeparturesResult {
        val response: StopTimesResponseDto = client.get("$baseUrl/v6/stoptimes", block).body()
        return DeparturesResult(
            departures = response.stopTimes.mapNotNull { it.toDeparture() },
            laterCursor = response.nextPageCursor,
        )
    }
}

internal fun LatLng.asParameter(): String = "$latitude,$longitude"

private fun PlanTarget.asParameter(): String =
    when (this) {
        is PlanTarget.Coordinates -> location.asParameter()
        is PlanTarget.Stop -> id
    }
