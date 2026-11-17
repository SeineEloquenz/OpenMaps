package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.FuelPrice
import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PricedStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

// Trimmed from real answers of each service.
private const val AUSTRIA_DIESEL =
    """[{"id":1,"name":"TMC","location":{"address":"Rechte Wienzeile 43","postalCode":"1050","city":"Wien",
    "latitude":48.196,"longitude":16.358},"open":true,"prices":[{"fuelType":"DIE","amount":1.995,"label":"Diesel"}]},
    {"id":2,"name":"Other","location":{"latitude":48.2,"longitude":16.36},"prices":[]}]"""

private const val AUSTRIA_SUPER =
    """[{"id":1,"name":"TMC","location":{"address":"Rechte Wienzeile 43","postalCode":"1050","city":"Wien",
    "latitude":48.196,"longitude":16.358},"prices":[{"fuelType":"SUP","amount":1.789}]}]"""

private fun tankPulsJson(price: Int) =
    """{"count":2,"items":[{"id":"a","name":"Aral Tankstelle","brandName":"Aral","street":"Durlacher Allee 46","postcode":"76131",
    "city":"Karlsruhe","lat":49.00763,"lng":8.42285,"priceCents":$price,"priceTs":"2026-11-16 11:06:06.202696+00"},
    {"id":"b","name":"Leer","lat":49.0,"lng":8.4,"priceCents":null,"priceTs":null}]}"""

// Shaped like an open-fuel-prices server's files.
private const val OFP_INDEX =
    """{"updated":"2026-11-16T22:10:52Z","cell_degrees":0.5,"cells":"cells/{x}/{y}.json","sources":[
    {"id":"it","country":"IT","attribution":"Ministero delle Imprese e del Made in Italy","attribution_url":"https://www.mimit.gov.it",
    "licence":"IODL-2.0","bounds":[35.4,6.6,47.1,18.6],"fetched":"2026-11-16T22:10:49Z","stations":21501}]}"""

private const val OFP_ROME_CELL =
    """{"stations":[{"id":"it:1","source":"it","lat":41.9,"lon":12.55,"brand":"Agip Eni","address":"Via Roma 1, Roma (RM)",
    "prices":[{"fuel":"petrol_95","price":1.799,"currency":"EUR","updated":"2026-11-15T07:00:00Z"},
    {"fuel":"hydrogen","price":9.9,"currency":"EUR","updated":null}]},
    {"id":"it:2","source":"it","lat":41.99,"lon":12.99,"prices":[{"fuel":"diesel","price":1.7,"currency":"EUR","updated":null}]}]}"""

class FuelSourcesTest {
    private val requests = mutableListOf<String>()
    private val requestsLock = Mutex()

    private fun client(answer: (String) -> String) =
        HttpClient(
            MockEngine { request ->
                requestsLock.withLock { requests += request.url.toString() }
                respond(answer(request.url.toString()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Test
    fun oriReadsOnlyTheCellsOfTheAreaAndSkipsCountriesItLacks() =
        runTest {
            val server = MutableStateFlow<String?>("https://ori.example.org/fuel/")
            val client =
                HttpClient(
                    MockEngine { request ->
                        val url = request.url.toString()
                        requestsLock.withLock { requests += url }
                        when {
                            url.endsWith(
                                "/sources.json",
                            ) -> respond(OFP_INDEX, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            url.endsWith("/cells/385/263.json") ->
                                respond(OFP_ROME_CELL, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            else -> respond("", HttpStatusCode.NotFound)
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val source = OriFuelPrices(client, baseUrl = { server.value })

            val station = source.stations(LatLng(41.9, 12.55), GeoBounds(41.85, 12.52, 41.95, 12.6)).single()
            val germany = source.stations(LatLng(49.0, 8.4), GeoBounds(48.9, 8.3, 49.1, 8.5))

            assertEquals(
                listOf("https://ori.example.org/fuel/v1/sources.json", "https://ori.example.org/fuel/v1/cells/385/263.json"),
                requests,
            )
            assertEquals(listOf(Fuel.PETROL_95), station.prices.map { it.fuel })
            assertEquals("Agip Eni", station.brand)
            assertEquals("Ministero delle Imprese e del Made in Italy", station.source.source)
            assertTrue(germany.isEmpty())

            server.value = null
            assertFalse(source.available)
        }

    @Test
    fun cellsCoverTheAreaAcrossCellEdges() {
        assertEquals(listOf(385 to 263), cellsCovering(GeoBounds(41.85, 12.52, 41.95, 12.6), 0.5))
        assertEquals(4, cellsCovering(GeoBounds(41.9, 12.9, 42.1, 13.1), 0.5).size)
    }

    @Test
    fun austriaJoinsTheFuelsOfOneStation() =
        runTest {
            val source =
                AustriaPrices(
                    client { url ->
                        if ("fuelType=DIE" in
                            url
                        ) {
                            AUSTRIA_DIESEL
                        } else if ("fuelType=SUP" in url) {
                            AUSTRIA_SUPER
                        } else {
                            "[]"
                        }
                    },
                )

            val stations = source.stations(LatLng(48.2, 16.37), GeoBounds(48.1, 16.2, 48.3, 16.5))

            assertEquals(3, requests.size)
            val station = stations.single()
            assertEquals(mapOf(Fuel.DIESEL to 1.995, Fuel.PETROL_95 to 1.789), station.prices.associate { it.fuel to it.price })
            assertEquals("Rechte Wienzeile 43, 1050 Wien", station.address)
        }

    @Test
    fun tankPulsJoinsOneRequestPerFuelAndReadsThousandths() =
        runTest {
            val source =
                TankPulsPrices(
                    client { url ->
                        when {
                            "fuel=diesel" in url -> tankPulsJson(2409)
                            "fuel=e10" in url -> tankPulsJson(2209)
                            else -> tankPulsJson(0)
                        }
                    },
                )

            val station = source.stations(LatLng(49.0, 8.4), GeoBounds(48.99, 8.38, 49.02, 8.43)).single()

            assertEquals(3, requests.size)
            assertEquals(mapOf(Fuel.DIESEL to 2.409, Fuel.E10 to 2.209), station.prices.associate { it.fuel to it.price })
            assertEquals(Instant.parse("2026-11-16T11:06:06.202696Z"), station.prices.first().updated)
            assertEquals("Durlacher Allee 46, 76131 Karlsruhe", station.address)
            assertEquals("Aral", station.brand)
        }

    @Test
    fun combinedAsksOnlyAvailableSourcesCoveringTheAreaAndSkipsFailures() =
        runTest {
            val asked = mutableListOf<String>()

            fun source(
                name: String,
                coverage: GeoBounds,
                available: Boolean = true,
                fails: Boolean = false,
            ) = object : FuelPriceSource {
                override val attribution = Attribution(name, "https://example.org")
                override val coverage = listOf(coverage)
                override val available = available

                override suspend fun stations(
                    center: LatLng,
                    area: GeoBounds,
                ): List<PricedStation> {
                    asked += name
                    if (fails) error("down")
                    return listOf(PricedStation(center, name, null, null, listOf(FuelPrice(Fuel.DIESEL, 1.5, "EUR", null)), attribution))
                }
            }
            val here = GeoBounds(49.0, 8.0, 50.0, 9.0)
            val combined =
                CombinedFuelPrices(
                    listOf(
                        source("covers", here),
                        source("elsewhere", GeoBounds(40.0, 0.0, 41.0, 1.0)),
                        source("no key", here, available = false),
                        source("broken", here, fails = true),
                    ),
                )

            val stations = combined.stations(LatLng(49.5, 8.5), GeoBounds(49.4, 8.4, 49.6, 8.6))

            assertEquals(listOf("covers"), stations.map { it.name })
            assertEquals(setOf("covers", "broken"), asked.toSet())
        }
}
