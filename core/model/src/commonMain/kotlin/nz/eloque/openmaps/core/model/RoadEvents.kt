package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

enum class RoadEventKind { ROADWORKS, ACCIDENT, OBSTRUCTION, CONDITIONS, CONGESTION, RESTRICTION, OTHER }

/** Something on a road that affects traffic, such as roadworks or a closure. */
data class RoadEvent(
    val id: String,
    val kind: RoadEventKind,
    val closed: Boolean,
    val lanesRestricted: Boolean,
    /** In km/h. */
    val speedLimit: Int?,
    val road: String?,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val description: String?,
    /** False when only the start and end are known and the drawn line between them is straight. */
    val exactGeometry: Boolean,
    val attribution: String,
    /** False while the publisher has not confirmed that the data may be reused. */
    val licenceConfirmed: Boolean,
)

/** Vector tiles of road events. Features carry true or false in [closedProperty] and [exactProperty]. */
data class RoadEventTiles(
    /** With `{z}`, `{x}` and `{y}` placeholders. */
    val urlTemplate: String,
    val layer: String,
    val minZoom: Int,
    val maxZoom: Int,
    val closedProperty: String,
    val exactProperty: String,
    val attribution: String,
)

interface RoadEvents {
    /** The tiles to draw, or null while no source is set up or reachable. */
    val tiles: Flow<RoadEventTiles?>

    /** The event a tile feature describes, from its properties as text. */
    fun event(properties: Map<String, String>): RoadEvent?
}
