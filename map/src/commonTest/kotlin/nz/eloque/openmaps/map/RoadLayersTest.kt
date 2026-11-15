package nz.eloque.openmaps.map

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class RoadLayersTest {
    private val route = listOf(LatLng(49.6, 6.1), LatLng(49.6, 6.2))

    @Test
    fun measuresToTheNearestSegment() {
        val beside = LatLng(49.6002, 6.15)
        assertEquals(22.2, distanceToLine(beside, route), 0.5)
    }

    @Test
    fun measuresPastTheEndToTheEndPoint() {
        val past = LatLng(49.6, 6.2014)
        assertEquals(101.0, distanceToLine(past, route), 2.0)
    }

    @Test
    fun readsPropertiesAsText() {
        val properties =
            buildJsonObject {
                put("road_closed", true)
                put("speed_limit", 60)
                put("road", "A5")
                put("description", JsonNull)
            }.asText()
        assertEquals(mapOf("road_closed" to "true", "speed_limit" to "60", "road" to "A5"), properties)
    }
}
