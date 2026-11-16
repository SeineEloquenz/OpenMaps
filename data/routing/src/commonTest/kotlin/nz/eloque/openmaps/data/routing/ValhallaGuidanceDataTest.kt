package nz.eloque.openmaps.data.routing

import nz.eloque.openmaps.core.model.Lane
import nz.eloque.openmaps.core.model.LaneDirection
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.navigation.core.models.IntersectionLanes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValhallaGuidanceDataTest {
    private val route = listOf(LatLng(49.0, 8.0), LatLng(49.0, 8.01), LatLng(49.0, 8.02), LatLng(49.0, 8.03))
    private val leg = route[0].distanceTo(route[1])

    @Test
    fun speedLimitsFollowTheSegmentsAlongTheRoute() {
        val limits =
            SpeedLimits.of(
                route,
                listOf(
                    TraceEdgeDto(speedLimit = 50, beginShapeIndex = 0, endShapeIndex = 1),
                    TraceEdgeDto(speedLimit = 0, beginShapeIndex = 1, endShapeIndex = 2),
                    TraceEdgeDto(speedLimit = 255, beginShapeIndex = 2, endShapeIndex = 3),
                ),
            )
        assertEquals(50, limits.at(leg / 2))
        assertNull(limits.at(leg * 1.5))
        assertNull(limits.at(leg * 2.5))
    }

    @Test
    fun mapsLanesWithTheirChosenDirection() {
        val lanes =
            listOf(
                IntersectionLanes(valid = false, active = false, validIndication = null, indications = listOf("left")),
                IntersectionLanes(valid = true, active = true, validIndication = "straight", indications = listOf("straight", "right")),
            ).map { it.toLane() }
        assertEquals(Lane(listOf(LaneDirection.LEFT), valid = false, recommended = false, chosen = null), lanes[0])
        assertEquals(
            Lane(listOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, recommended = true, chosen = LaneDirection.STRAIGHT),
            lanes[1],
        )
    }
}
