package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.RoadEvent
import nz.eloque.openmaps.core.model.RoadEventKind
import nz.eloque.openmaps.core.model.RoadEventTiles
import nz.eloque.openmaps.core.model.RoadEvents
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
internal data class SourcesDto(
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
internal data class TileMetadataDto(
    val minzoom: String,
    val maxzoom: String,
)

@Serializable
internal data class SourceDto(
    val id: String,
    val attribution: String,
    val licence: String,
)

/** Road events published by an open-road-incidents server. */
internal class OpenRoadIncidents(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
) : RoadEvents {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val tiles: Flow<RoadEventTiles?> =
        baseUrl.transformLatest { configured ->
            val base = configured?.takeIf { it.isNotBlank() }?.trimEnd('/')
            emit(null)
            if (base == null) return@transformLatest
            emitAll(tilesFrom(base))
        }

    private fun tilesFrom(base: String): Flow<RoadEventTiles> =
        flow {
            var pause = RetryFirst
            while (true) {
                val loaded =
                    try {
                        client.get("$base/v1/sources.json").body<SourcesDto>().sources to
                            client.get("$base/v1/tiles/metadata.json").body<TileMetadataDto>()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (loaded != null) {
                    val (sources, metadata) = loaded
                    emit(
                        RoadEventTiles(
                            urlTemplate = "$base/v1/tiles/{z}/{x}/{y}.pbf",
                            layer = "incidents",
                            minZoom = metadata.minzoom.toInt(),
                            maxZoom = metadata.maxzoom.toInt(),
                            closedProperty = "road_closed",
                            exactProperty = "geometry_exact",
                            attribution = sources.map { it.attribution }.distinct().joinToString("<br>"),
                        ),
                    )
                    return@flow
                }
                delay(pause)
                pause = minOf(pause * 2, RetryMax)
            }
        }

    override fun event(properties: Map<String, String>): RoadEvent? {
        val id = properties["id"] ?: return null
        return RoadEvent(
            id = id,
            kind = kinds[properties["kind"]] ?: RoadEventKind.OTHER,
            closed = properties["road_closed"] == "true",
            lanesRestricted = properties["lanes_restricted"] == "true",
            speedLimit = properties["speed_limit"]?.toDoubleOrNull()?.toInt(),
            road = properties["road"]?.takeIf { it.isNotBlank() },
            validFrom = properties["valid_from"]?.let(::instant),
            validUntil = properties["valid_until"]?.let(::instant),
            description = properties["description"]?.takeIf { it.isNotBlank() },
            exactGeometry = properties["geometry_exact"] != "false",
            attribution = properties["attribution"].orEmpty(),
            licenceConfirmed = properties["licence"].let { it != null && it != UNCONFIRMED },
        )
    }
}

private fun instant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

private val kinds =
    mapOf(
        "roadworks" to RoadEventKind.ROADWORKS,
        "accident" to RoadEventKind.ACCIDENT,
        "obstruction" to RoadEventKind.OBSTRUCTION,
        "conditions" to RoadEventKind.CONDITIONS,
        "congestion" to RoadEventKind.CONGESTION,
        "restriction" to RoadEventKind.RESTRICTION,
    )

private const val UNCONFIRMED = "unconfirmed"
private val RetryFirst = 5.seconds
private val RetryMax = 5.minutes
