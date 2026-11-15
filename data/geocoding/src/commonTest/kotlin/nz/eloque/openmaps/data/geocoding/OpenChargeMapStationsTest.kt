package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.Charger
import nz.eloque.openmaps.core.model.Connector
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Shaped like the output of the Open Charge Map v3 poi endpoint, trimmed to the fields read.
private const val STATIONS_JSON =
    """[{"ID":1,"AddressInfo":{"Title":"Karlsruhe Hbf","AddressLine1":"Bahnhofplatz 1","Town":"Karlsruhe","Postcode":"76137",
    "Latitude":48.99,"Longitude":8.40},"OperatorInfo":{"Title":"EnBW","WebsiteURL":"https://www.enbw.com"},
    "UsageCost":"0,59 EUR/kWh","NumberOfPoints":6,"StatusType":{"IsOperational":true},
    "Connections":[{"ConnectionTypeID":33,"PowerKW":150.0,"Quantity":2},{"ConnectionTypeID":33,"PowerKW":50.0},
    {"ConnectionTypeID":25,"PowerKW":22.0,"Quantity":4},{"ConnectionTypeID":999,"PowerKW":3.7}]},
    {"ID":2,"AddressInfo":{"Title":"Broken","Latitude":48.0,"Longitude":8.0},"StatusType":{"IsOperational":false}},
    {"ID":3,"AddressInfo":{"Title":"Tiefgarage","Latitude":49.1,"Longitude":8.4},"OperatorInfo":{"Title":"(Business Owner at Location)"},
    "Connections":[{"ConnectionTypeID":16,"PowerKW":3.7,"Quantity":2}]}]"""

class OpenChargeMapStationsTest {
    private val headers = mutableListOf<String?>()

    private fun source(key: String?) =
        OpenChargeMapStations(
            HttpClient(
                MockEngine { request ->
                    headers += request.headers["X-API-Key"]
                    respond(STATIONS_JSON, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
        ) { key }

    @Test
    fun mapsStationsToOsmStyleTags() =
        runTest {
            val stations = source("secret").stations(LatLng(49.0, 8.4), GeoBounds(48.9, 8.3, 49.1, 8.5))

            assertEquals(2, stations.size)
            val (place, details) = stations.first()
            assertEquals("Karlsruhe Hbf", place.name)
            assertEquals(PlaceId("ocm:1"), place.id)
            assertEquals("Bahnhofplatz 1, 76137 Karlsruhe", details.address)
            assertEquals("EnBW", details.operator)
            assertEquals(listOf(Charger(Connector.CCS, 150, 3), Charger(Connector.TYPE_2, 22, 4)), details.chargers)
            assertEquals("secret", headers.single())
            val unknownOperator = stations.last().second
            assertEquals(null, unknownOperator.operator)
            assertEquals(listOf(Charger(Connector.CEE, 4, 2)), unknownOperator.chargers)
        }

    @Test
    fun unavailableWithoutKey() =
        runTest {
            val source = source(null)

            assertFalse(source.available)
            assertTrue(source.stations(LatLng(49.0, 8.4), GeoBounds(48.9, 8.3, 49.1, 8.5)).isEmpty())
            assertTrue(headers.isEmpty())
        }
}
