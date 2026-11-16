package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
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
import kotlin.test.assertTrue
import kotlin.time.Instant

// Trimmed from real answers of each service.
private const val FRANCE_JSON =
    """{"total_count":1,"results":[{"geom":{"lon":7.76077,"lat":48.58715},"adresse":"9 AVENUE ALSACE","ville":"STRASBOURG",
    "cp":"67000","gazole_prix":2.369,"gazole_maj":"2026-11-16T06:00:00+00:00","sp95_prix":null,"sp95_maj":null,
    "e10_prix":2.129,"e10_maj":"2026-11-16T06:00:00+00:00","e85_prix":0.999,"e85_maj":"2026-11-16T06:00:00+00:00"}]}"""

private const val AUSTRIA_DIESEL =
    """[{"id":1,"name":"TMC","location":{"address":"Rechte Wienzeile 43","postalCode":"1050","city":"Wien",
    "latitude":48.196,"longitude":16.358},"open":true,"prices":[{"fuelType":"DIE","amount":1.995,"label":"Diesel"}]},
    {"id":2,"name":"Other","location":{"latitude":48.2,"longitude":16.36},"prices":[]}]"""

private const val AUSTRIA_SUPER =
    """[{"id":1,"name":"TMC","location":{"address":"Rechte Wienzeile 43","postalCode":"1050","city":"Wien",
    "latitude":48.196,"longitude":16.358},"prices":[{"fuelType":"SUP","amount":1.789}]}]"""

private const val TANKERKOENIG_JSON =
    """{"ok":true,"stations":[{"id":"a","name":"Aral Tankstelle","brand":"ARAL","street":"DURLACHER ALLEE","houseNumber":"7 ",
    "postCode":76131,"place":"KARLSRUHE","lat":49.009,"lng":8.42,"dist":0.5,"diesel":1.659,"e5":false,"e10":1.759,"isOpen":true}]}"""

private fun tankPulsJson(price: Int) =
    """{"count":2,"items":[{"id":"a","name":"Aral Tankstelle","brandName":"Aral","street":"Durlacher Allee 46","postcode":"76131",
    "city":"Karlsruhe","lat":49.00763,"lng":8.42285,"priceCents":$price,"priceTs":"2026-11-16 11:06:06.202696+00"},
    {"id":"b","name":"Leer","lat":49.0,"lng":8.4,"priceCents":null,"priceTs":null}]}"""

class FuelSourcesTest {
    private val requests = mutableListOf<String>()

    private fun client(answer: (String) -> String) =
        HttpClient(
            MockEngine { request ->
                requests += request.url.toString()
                respond(answer(request.url.toString()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Test
    fun franceReadsPricesAsParisTime() =
        runTest {
            val source = FrancePrices(client { FRANCE_JSON })

            val station = source.stations(LatLng(48.58, 7.75), GeoBounds(48.55, 7.70, 48.62, 7.80)).single()

            assertEquals(listOf(Fuel.DIESEL, Fuel.E10, Fuel.E85), station.prices.map { it.fuel })
            assertEquals(Instant.parse("2026-11-16T05:00:00Z"), station.prices.first().updated)
            assertEquals("9 Avenue Alsace, 67000 Strasbourg", station.address)
            assertTrue(requests.single().contains("in_bbox"))
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
    fun tankerkoenigNeedsAKeyAndSkipsFuelsMarkedFalse() =
        runTest {
            var key: String? = null
            val source = TankerkoenigPrices(client { TANKERKOENIG_JSON }) { key }
            assertEquals(false, source.available)

            key = "secret"
            val station = source.stations(LatLng(49.0, 8.4), GeoBounds(48.95, 8.35, 49.05, 8.45)).single()

            assertEquals(listOf(Fuel.DIESEL, Fuel.E10), station.prices.map { it.fuel })
            assertEquals("ARAL", station.brand)
            assertEquals("Durlacher Allee 7, 76131 Karlsruhe", station.address)
            assertTrue(requests.single().contains("apikey=secret"))
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
