package nz.eloque.openmaps.data.routing

import kotlinx.coroutines.test.runTest
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ValhallaServersTest {
    private val servers =
        ValhallaServers(car = { "https://ori.example/" }, active = { "https://walk.example" }, activeInterval = 200.milliseconds)

    @Test
    fun drivingAsksTheCarServerFirstAndEverythingElseOnlyTheActiveOne() {
        assertEquals(listOf("https://ori.example", "https://walk.example"), servers.urls(TransportMode.CAR))
        assertEquals(listOf("https://walk.example"), servers.urls(TransportMode.BIKE))
        assertEquals(
            listOf("https://walk.example"),
            ValhallaServers(car = {
                " "
            }, active = { "https://walk.example/" }).urls(TransportMode.CAR),
        )
        assertEquals(emptyList(), ValhallaServers(car = { null }, active = { null }).urls(TransportMode.WALK))
    }

    @Test
    fun fallsThroughToTheNextServerWhenOneHasNoRouteOrFails() =
        runTest {
            val asked = mutableListOf<String>()

            val answer = servers.first(TransportMode.CAR) { url -> url.also { asked += it }.takeIf { "walk" in it } }
            val failedFirst = servers.first(TransportMode.CAR) { url -> if ("ori" in url) error("down") else url }

            assertEquals("https://walk.example", answer)
            assertEquals(listOf("https://ori.example", "https://walk.example"), asked)
            assertEquals("https://walk.example", failedFirst)
            assertNull(servers.first(TransportMode.CAR) { null })
            assertFailsWith<IllegalStateException> { servers.first(TransportMode.CAR) { error("down") } }
        }

    @Test
    fun spacesRequestsToTheActiveServerButNotToTheCarServer() =
        runTest {
            servers.at("https://ori.example") {}
            servers.at("https://ori.example") {}
            assertEquals(0, testScheduler.currentTime)

            servers.at("https://walk.example") {}
            servers.at("https://walk.example") {}
            assertTrue(testScheduler.currentTime >= 150, "waited ${testScheduler.currentTime} ms of virtual time")
        }
}
