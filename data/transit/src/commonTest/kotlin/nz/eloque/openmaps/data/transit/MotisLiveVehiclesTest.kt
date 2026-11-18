package nz.eloque.openmaps.data.transit

import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// Trimmed from a real answer of Transitous for central Karlsruhe.
private const val SEGMENT =
    """{"trips":[{"tripId":"20261117_24:45_de-KVV_547.T0.21-1-E-s26-6.1.R","displayName":"1"}],"routeColor":"e30613",
    "mode":"TRAM","distance":300.5,"from":{"name":"Europaplatz","lat":49.01088699999999,"lon":8.376197000000001},
    "to":{"name":"Herrenstraße","lat":49.0109,"lon":8.3995},"departure":"2026-11-17T23:56:00Z","arrival":"2026-11-17T23:57:00Z",
    "scheduledDeparture":"2026-11-17T23:56:00Z","scheduledArrival":"2026-11-17T23:57:00Z","realTime":true,
    "polyline":"amcjHg~br@@U@_@DsCDaCLoH@y@B_A@}@D{B@a@@aA@M?YHyD@[F_E"}"""

class MotisLiveVehiclesTest {
    @Test
    fun segmentsBecomeRunsAlongTheirPath() {
        val run = Json { ignoreUnknownKeys = true }.decodeFromString<TripSegmentDto>(SEGMENT).toRun()!!
        assertEquals("1", run.name)
        assertEquals(TransportMode.TRAM, run.mode)
        assertEquals(0xFFE30613, run.color)
        assertEquals("Herrenstraße", run.nextStop)
        assertEquals(Instant.parse("2026-11-17T23:57:00Z"), run.arrival)
        assertEquals(49.01089, run.path.first().latitude, 1e-5)
        assertEquals(8.3762, run.path.first().longitude, 1e-4)
    }
}
