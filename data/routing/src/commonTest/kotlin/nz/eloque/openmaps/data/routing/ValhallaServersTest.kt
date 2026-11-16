package nz.eloque.openmaps.data.routing

import kotlinx.coroutines.test.runTest
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class ValhallaServersTest {
    @Test
    fun drivingUsesTheCarServerAndEverythingElseTheActiveOne() {
        val servers = ValhallaServers(car = { "https://ori.example/" }, active = { "https://walk.example" })

        assertEquals("https://ori.example", servers.url(TransportMode.CAR))
        assertEquals("https://walk.example", servers.url(TransportMode.BIKE))
        assertEquals("https://walk.example", servers.url(TransportMode.WALK))
        assertNull(ValhallaServers(car = { " " }, active = { null }).url(TransportMode.CAR))
    }

    @Test
    fun spacesRequestsToTheActiveServerButNotToTheCarServer() =
        runTest {
            val servers =
                ValhallaServers(car = { "https://ori.example" }, active = { "https://walk.example" }, activeInterval = 200.milliseconds)
            val start = TimeSource.Monotonic.markNow()

            servers.call(TransportMode.CAR) {}
            servers.call(TransportMode.CAR) {}
            servers.call(TransportMode.WALK) {}
            servers.call(TransportMode.BIKE) {}

            assertTrue(testScheduler.currentTime >= 200, "waited ${testScheduler.currentTime} ms of virtual time")
            assertTrue(start.elapsedNow() < 2000.milliseconds)
        }
}
