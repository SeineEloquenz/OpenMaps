package nz.eloque.openmaps.data.routing

import kotlinx.coroutines.CancellationException
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.RoadEvents
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.StreetRequest
import nz.eloque.openmaps.core.model.StreetRouter
import org.koin.core.scope.Scope
import org.koin.dsl.module

/** A Valhalla server for car routes, which knows current closures when it runs open-road-incidents. */
val RoutingServer =
    ServiceSetting(
        "server.routing",
        ServiceRole.STREET_ROUTING,
        ServiceSettingKind.SERVER_URL,
        "Valhalla",
        default = null,
        helpUrl = "https://github.com/valhalla/valhalla",
    )

const val FOSSGIS_VALHALLA = "https://valhalla1.openstreetmap.de"

/** A Valhalla server with footpaths and cycleways for walking and cycling routes. */
val ActiveRoutingServer =
    ServiceSetting(
        "server.routing.active",
        ServiceRole.ACTIVE_ROUTING,
        ServiceSettingKind.SERVER_URL,
        "Valhalla",
        default = FOSSGIS_VALHALLA,
        helpUrl = "https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/",
    )

/** An open-road-incidents server for roadworks and closures on the map. */
val RoadEventsServer =
    ServiceSetting(
        "server.roads",
        ServiceRole.ROAD_EVENTS,
        ServiceSettingKind.SERVER_URL,
        "OpenRoadIncidents",
        default = null,
    )

/** The settings this module's sources read. */
val routingServiceSettings = listOf(RoutingServer, ActiveRoutingServer, RoadEventsServer)

/** Asks [primary] and [fallback] for the modes [primary] has no route for, such as outside its map or when it is not set up. */
internal class FallbackStreetRouter(
    private val primary: StreetRouter,
    private val fallback: StreetRouter,
) : StreetRouter {
    override suspend fun route(request: StreetRequest): List<Itinerary> {
        val routes =
            try {
                primary.route(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        val missing = request.modes - routes.mapNotNull { it.legs.firstOrNull()?.mode }.toSet()
        return if (missing.isEmpty()) routes else routes + fallback.route(request.copy(modes = missing))
    }
}

/**
 * Routes cars through [RoutingServer], walking and cycling through [ActiveRoutingServer], and each mode through
 * [fallback] while its server is not set or fails.
 * Navigation needs the server and a [LocationUpdates] from the app. [language] is a BCP 47 tag for spoken directions.
 */
fun routingModule(
    fallback: Scope.() -> StreetRouter,
    language: () -> String? = { null },
) = module {
    single {
        val settings = get<SettingsStore>()
        ValhallaServers(
            car = { settings.serviceValue(RoutingServer.id).value },
            active = { settings.serviceValue(ActiveRoutingServer.id).value ?: FOSSGIS_VALHALLA },
        )
    }
    single<StreetRouter> { FallbackStreetRouter(ValhallaStreetRouter(get(), get()), fallback()) }
    single<Navigator> {
        val settings = get<SettingsStore>()
        ValhallaNavigator(
            ValhallaNavigationRoutes(
                get(),
                get(),
                language = language,
                units = { settings.distanceUnit.value },
            ),
            get(),
        )
    }
    single<RoadEvents> { OpenRoadIncidents(get(), get<SettingsStore>().serviceValue(RoadEventsServer.id)) }
}
