package nz.eloque.openmaps.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.RadarColourScheme
import nz.eloque.openmaps.core.model.RadarFrame
import nz.eloque.openmaps.core.model.RadarFrames
import nz.eloque.openmaps.core.model.RadarOptions
import nz.eloque.openmaps.core.model.RainRadar
import nz.eloque.openmaps.core.model.SatelliteImage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
internal data class WeatherMapsDto(
    val host: String,
    val radar: RadarDto,
    val satellite: SatelliteDto? = null,
)

@Serializable
internal data class SatelliteDto(
    val infrared: List<FrameDto> = emptyList(),
)

@Serializable
internal data class RadarDto(
    val past: List<FrameDto> = emptyList(),
    val nowcast: List<FrameDto> = emptyList(),
    val colorSchemes: List<ColourSchemeDto> = emptyList(),
)

@Serializable
internal data class ColourSchemeDto(
    val id: Int,
    val name: String,
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
    private val options: Flow<RadarOptions>,
    private val refresh: Duration = 5.minutes,
) : RainRadar {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val frames: Flow<RadarFrames?> =
        baseUrl.transformLatest { configured ->
            val base = configured?.takeIf { it.isNotBlank() }?.trimEnd('/')
            emit(null)
            if (base == null) return@transformLatest
            emitAll(combine(mapsFrom(base), options) { maps, options -> maps.toFrames(options) })
        }

    private fun mapsFrom(base: String): Flow<WeatherMapsDto> =
        flow {
            var pause = RetryFirst
            while (true) {
                val loaded =
                    try {
                        client.get("$base/public/weather-maps.json").body<WeatherMapsDto>()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (loaded != null) {
                    emit(loaded)
                    pause = RetryFirst
                    delay(refresh)
                } else {
                    delay(pause)
                    pause = minOf(pause * 2, refresh)
                }
            }
        }.distinctUntilChanged()
}

/** The last hour of measured frames and the forecast. Older frames would only add tile requests. */
internal fun WeatherMapsDto.toFrames(options: RadarOptions = RadarOptions()): RadarFrames {
    val root = host.trimEnd('/')
    val schemes = radar.colorSchemes.map { RadarColourScheme(it.id, it.name) }
    val scheme =
        options.colourScheme.takeIf { id -> schemes.isEmpty() || schemes.any { it.id == id } } ?: RadarOptions.DEFAULT_COLOUR_SCHEME

    fun frame(
        dto: FrameDto,
        forecast: Boolean,
    ): RadarFrame {
        val template = "$root${dto.path}/$TILE_SIZE/{z}/{x}/{y}/$scheme/$SMOOTH_SNOW.$FORMAT"
        return RadarFrame(
            Instant.fromEpochSeconds(dto.time),
            forecast,
            template + query(options, "dark"),
            template + query(options, "light"),
        )
    }
    val newest = radar.past.maxOfOrNull { it.time } ?: 0
    val past = radar.past.filter { newest - it.time < PAST_SECONDS }.sortedBy { it.time }
    return RadarFrames(
        frames = past.map { frame(it, forecast = false) } + radar.nowcast.sortedBy { it.time }.map { frame(it, forecast = true) },
        maxZoom = MAX_ZOOM,
        tileSize = TILE_SIZE,
        attribution = ATTRIBUTION,
        clouds =
            satellite?.infrared.orEmpty().sortedBy { it.time }.map {
                SatelliteImage(
                    Instant.fromEpochSeconds(it.time),
                    "$root${it.path}/$TILE_SIZE/{z}/{x}/{y}/0/0_0.$FORMAT",
                    SATELLITE_MAX_ZOOM,
                    TILE_SIZE,
                )
            },
        colourSchemes = schemes,
    )
}

/** Tile options, with [style] picked to stand out on the map below. */
private fun query(
    options: RadarOptions,
    style: String,
): String {
    val parameters =
        listOfNotNull(
            "arrows=$style".takeIf { options.arrows },
            "lightning=$LIGHTNING".takeIf { options.lightning },
            "cells=$style".takeIf { options.stormCells },
        )
    return if (parameters.isEmpty()) "" else parameters.joinToString("&", prefix = "?")
}

// Twice the usual size at one zoom level less looks the same, with a quarter of the requests and fewer bytes.
private const val TILE_SIZE = 512
private const val FORMAT = "webp"

// Smoothed, with snow drawn in its own colours.
private const val SMOOTH_SNOW = "1_1"

private const val LIGHTNING = "dots"

private const val PAST_SECONDS = 3600

// About 400 m per pixel, already finer than the 1 km radar.
private const val MAX_ZOOM = 7

// The global composite is a few kilometres per pixel, so higher zooms only enlarge it.
private const val SATELLITE_MAX_ZOOM = 5
private const val ATTRIBUTION =
    "<a href=\"https://github.com/JoshuaKimsey/LibreWXR\">LibreWXR</a>, radar © EUMETNET OPERA, DPC, NOAA and others"

private val RetryFirst = 15.seconds
