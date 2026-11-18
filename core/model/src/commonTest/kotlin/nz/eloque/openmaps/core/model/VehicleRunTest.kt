package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VehicleRunTest {
    private val start = Instant.parse("2026-11-18T08:00:00Z")

    // An L-shaped path whose first leg is three times as long as the second.
    private val run =
        VehicleRun(
            tripId = "t",
            name = "1",
            mode = TransportMode.TRAM,
            color = null,
            nextStop = "Europaplatz",
            departure = start,
            arrival = start + 80.seconds,
            path =
                listOf(
                    LatLng(49.0, 8.0),
                    LatLng(49.003, 8.0),
                    LatLng(
                        49.003,
                        8.0 + 0.001 / kotlin.math.cos(49.003 * kotlin.math.PI / 180),
                    ),
                ),
            realTime = true,
        )

    @Test
    fun theVehicleMovesEvenlyAlongThePath() {
        assertEquals(LatLng(49.0, 8.0), run.positionAt(start))
        assertEquals(49.0015, run.positionAt(start + 30.seconds)!!.latitude, 1e-5)
        assertEquals(49.003, run.positionAt(start + 70.seconds)!!.latitude, 1e-9)
        assertEquals(8.0, run.positionAt(start + 60.seconds)!!.longitude, 2e-5)
    }

    @Test
    fun outsideTheRunThereIsNoPosition() {
        assertNull(run.positionAt(start - 1.seconds))
        assertNull(run.positionAt(start + 81.seconds))
    }
}
