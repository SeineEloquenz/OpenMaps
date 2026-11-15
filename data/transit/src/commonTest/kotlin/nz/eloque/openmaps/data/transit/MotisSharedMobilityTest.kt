package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.VehicleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Trimmed from a Transitous /api/v1/rentals response in Karlsruhe.
private const val RENTALS_JSON =
    """{"providerGroups":[{"id":"KVV.nextbike","name":"KVV.nextbike"},{"id":"bolt-group","name":"Bolt"}],"providers":[],"zones":[],
    "stations":[{"id":"S1","providerGroupId":"KVV.nextbike","name":"Fächerbad","lat":49.02,"lon":8.43,"isRenting":true,
    "numVehiclesAvailable":3,"formFactors":["BICYCLE"],"vehicleDocksAvailable":{"t1":2,"t2":1},"rentalUriAndroid":"",
    "rentalUriWeb":"https://nxtb.it/p/1"},
    {"id":"S2","providerGroupId":"KVV.nextbike","name":"Closed","lat":49.0,"lon":8.4,"isRenting":false}],
    "vehicles":[{"id":"V1","providerGroupId":"bolt-group","lat":49.0,"lon":8.39,"formFactor":"SCOOTER_STANDING",
    "propulsionType":"ELECTRIC","stationId":"","rentalUriAndroid":"bolt://scooter/1"},
    {"id":"V1","providerGroupId":"bolt-group","lat":49.0,"lon":8.39,"formFactor":"SCOOTER_STANDING","propulsionType":"ELECTRIC","stationId":""},
    {"id":"V2","providerGroupId":"KVV.nextbike","lat":49.02,"lon":8.43,"formFactor":"BICYCLE","stationId":"S1"},
    {"id":"V3","providerGroupId":"bolt-group","lat":49.0,"lon":8.39,"formFactor":"SCOOTER_STANDING","stationId":"","isReserved":true}]}"""

class MotisSharedMobilityTest {
    private val requests = mutableListOf<Url>()

    private val sharing =
        MotisSharedMobility(
            HttpClient(
                MockEngine { request ->
                    requests += request.url
                    respond(RENTALS_JSON, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
        ) { TRANSITOUS_API }

    @Test
    fun mapsRentingStationsAndFreeVehiclesOnce() =
        runTest {
            val snapshot = sharing.available(GeoBounds(48.99, 8.38, 49.01, 8.41))

            val station = snapshot.stations.single()
            assertEquals("Fächerbad", station.name)
            assertEquals(3, station.available)
            assertEquals(3, station.freeDocks)
            assertEquals(null, station.booking.app)
            assertEquals("https://nxtb.it/p/1", station.booking.web)
            val scooter = snapshot.vehicles.single()
            assertEquals("Bolt", scooter.operator)
            assertEquals(VehicleKind.SCOOTER, scooter.kind)
            assertTrue(scooter.electric)
            assertEquals("bolt://scooter/1", scooter.booking.app)
            assertEquals("48.99,8.41", requests.single().parameters["min"])
            assertEquals("false", requests.single().parameters["withZones"])
        }
}
