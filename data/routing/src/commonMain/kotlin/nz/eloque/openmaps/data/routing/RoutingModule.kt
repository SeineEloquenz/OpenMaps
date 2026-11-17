package nz.eloque.openmaps.data.routing

import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.RoadEvents
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.StreetRouter
import org.koin.dsl.module

/** A Valhalla server for car routes, which knows current closures when it runs open-road-incidents. */
val RoutingServer =
    ServiceSetting(
        "server.routing",
        ServiceRole.STREET_ROUTING,
        ServiceSettingKind.SERVER_URL,
        "Valhalla",
        default = ORI_SERVER,
        helpUrl = "https://github.com/valhalla/valhalla",
    )

const val FOSSGIS_VALHALLA = "https://valhalla1.openstreetmap.de"

/** A Valhalla server with footpaths and cycleways for walking and cycling routes, also asked for car routes [RoutingServer] has none for. */
val ActiveRoutingServer =
    ServiceSetting(
        "server.routing.active",
        ServiceRole.ACTIVE_ROUTING,
        ServiceSettingKind.SERVER_URL,
        "Valhalla",
        default = FOSSGIS_VALHALLA,
        helpUrl = "https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/",
    )

internal const val ORI_SERVER = "https://ori.eloque.nz"

/** An open-road-incidents server for roadworks and closures on the map. */
val RoadEventsServer =
    ServiceSetting(
        "server.roads",
        ServiceRole.ROAD_EVENTS,
        ServiceSettingKind.SERVER_URL,
        "OpenRoadIncidents",
        default = ORI_SERVER,
    )

/** The settings this module's sources read. */
val routingServiceSettings = listOf(RoutingServer, ActiveRoutingServer, RoadEventsServer)

/**
 * Routes cars through [RoutingServer] and then [ActiveRoutingServer], walking and cycling through [ActiveRoutingServer].
 * Navigation needs the server and a [LocationUpdates] from the app. [language] is a BCP 47 tag for spoken directions.
 */
fun routingModule(language: () -> String? = { null }) =
    module {
        single {
            val settings = get<SettingsStore>()
            ValhallaServers(
                car = { settings.serviceValue(RoutingServer.id).value ?: ORI_SERVER },
                active = { settings.serviceValue(ActiveRoutingServer.id).value ?: FOSSGIS_VALHALLA },
            )
        }
        single<StreetRouter> { ValhallaStreetRouter(get(), get()) }
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
        single<RoadEvents> { OpenRoadIncidents(get(), get<SettingsStore>().serviceValue(RoadEventsServer.id).map { it ?: ORI_SERVER }) }
    }
