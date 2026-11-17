package nz.eloque.openmaps.data.weather

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
import nz.eloque.openmaps.core.model.RadarFrame
import nz.eloque.openmaps.core.model.RadarFrames
import nz.eloque.openmaps.core.model.RainRadar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
internal data class WeatherMapsDto(
    val host: String,
    val radar: RadarDto,
)

@Serializable
internal data class RadarDto(
    val past: List<FrameDto> = emptyList(),
    val nowcast: List<FrameDto> = emptyList(),
)

@Serializable
internal data class FrameDto(
    val time: Long,
    val path: String,
)

/** Radar frames from a server speaking the Rain Viewer v2 API, such as LibreWXR. */
internal class RainViewerRadar(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
    private val refresh: Duration = 5.minutes,
) : RainRadar {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val frames: Flow<RadarFrames?> =
        baseUrl.transformLatest { configured ->
            val base = configured?.takeIf { it.isNotBlank() }?.trimEnd('/')
            emit(null)
            if (base == null) return@transformLatest
            emitAll(framesFrom(base))
        }

    private fun framesFrom(base: String): Flow<RadarFrames> =
        flow {
            var last: RadarFrames? = null
            var pause = RetryFirst
            while (true) {
                val loaded =
                    try {
                        client.get("$base/public/weather-maps.json").body<WeatherMapsDto>().toFrames()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (loaded != null) {
                    if (loaded != last) emit(loaded)
                    last = loaded
                    pause = RetryFirst
                    delay(refresh)
                } else {
                    delay(pause)
                    pause = minOf(pause * 2, refresh)
                }
            }
        }
}

/** The last hour of measured frames and the forecast. Older frames would only add tile requests. */
internal fun WeatherMapsDto.toFrames(): RadarFrames {
    fun frame(
        dto: FrameDto,
        forecast: Boolean,
    ) = RadarFrame(
        Instant.fromEpochSeconds(dto.time),
        forecast,
        "${host.trimEnd('/')}${dto.path}/$TILE_SIZE/{z}/{x}/{y}/$COLOUR_SCHEME/$SMOOTH_SNOW.png",
    )
    val newest = radar.past.maxOfOrNull { it.time } ?: 0
    val past = radar.past.filter { newest - it.time < PAST_SECONDS }.sortedBy { it.time }
    return RadarFrames(
        frames = past.map { frame(it, forecast = false) } + radar.nowcast.sortedBy { it.time }.map { frame(it, forecast = true) },
        maxZoom = MAX_ZOOM,
        attribution = ATTRIBUTION,
    )
}

private const val TILE_SIZE = 256

// Universal Blue, the Rain Viewer default.
private const val COLOUR_SCHEME = 2

// Smoothed, with snow drawn in its own colours.
private const val SMOOTH_SNOW = "1_1"

private const val PAST_SECONDS = 3600
private const val MAX_ZOOM = 8
private const val ATTRIBUTION =
    "<a href=\"https://github.com/JoshuaKimsey/LibreWXR\">LibreWXR</a>, radar © EUMETNET OPERA, DPC, NOAA and others"

private val RetryFirst = 15.seconds
