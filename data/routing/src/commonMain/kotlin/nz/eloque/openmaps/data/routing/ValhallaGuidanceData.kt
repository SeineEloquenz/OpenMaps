package nz.eloque.openmaps.data.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Lane
import nz.eloque.openmaps.core.model.LaneDirection
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.navigation.core.models.IntersectionLanes

@Serializable
internal data class TraceAttributesRequestDto(
    @SerialName("encoded_polyline") val encodedPolyline: String,
    val costing: String,
    @SerialName("shape_match") val shapeMatch: String,
    val filters: TraceFiltersDto,
)

@Serializable
internal data class TraceFiltersDto(
    val attributes: List<String>,
    val action: String,
)

@Serializable
internal data class TraceAttributesDto(
    val edges: List<TraceEdgeDto> = emptyList(),
)

@Serializable
internal data class TraceEdgeDto(
    @SerialName("speed_limit") val speedLimit: Int? = null,
    @SerialName("begin_shape_index") val beginShapeIndex: Int,
    @SerialName("end_shape_index") val endShapeIndex: Int,
)

/** Speed limits along a route by distance from its start. */
internal class SpeedLimits(
    private val spans: List<Span>,
) {
    data class Span(
        val fromMeters: Double,
        val toMeters: Double,
        val kph: Int,
    )

    /** In km/h, null where unknown or unlimited. */
    fun at(meters: Double): Int? = spans.firstOrNull { meters >= it.fromMeters && meters < it.toMeters }?.kph

    companion object {
        fun of(
            route: List<LatLng>,
            edges: List<TraceEdgeDto>,
        ): SpeedLimits {
            val along = DoubleArray(route.size)
            for (i in 1 until route.size) along[i] = along[i - 1] + route[i - 1].distanceTo(route[i])

            fun at(index: Int) = along[index.coerceIn(0, route.lastIndex)]
            return SpeedLimits(
                edges.mapNotNull { edge ->
                    val kph = edge.speedLimit?.takeIf { it in 1 until UNLIMITED } ?: return@mapNotNull null
                    Span(at(edge.beginShapeIndex), at(edge.endShapeIndex), kph)
                },
            )
        }
    }
}

internal fun IntersectionLanes.toLane(): Lane {
    val valid = this.valid == true
    return Lane(
        directions = indications.orEmpty().mapNotNull(::laneDirection),
        valid = valid,
        recommended = active ?: valid,
        chosen = validIndication?.let(::laneDirection),
    )
}

private fun laneDirection(indication: String): LaneDirection? =
    when (indication) {
        "sharp left" -> LaneDirection.SHARP_LEFT
        "left" -> LaneDirection.LEFT
        "slight left" -> LaneDirection.SLIGHT_LEFT
        "straight" -> LaneDirection.STRAIGHT
        "slight right" -> LaneDirection.SLIGHT_RIGHT
        "right" -> LaneDirection.RIGHT
        "sharp right" -> LaneDirection.SHARP_RIGHT
        "uturn" -> LaneDirection.U_TURN
        else -> null
    }

/** Valhalla's speed limit for roads without one. */
private const val UNLIMITED = 255
