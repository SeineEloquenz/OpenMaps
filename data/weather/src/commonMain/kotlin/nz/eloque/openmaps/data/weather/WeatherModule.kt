package nz.eloque.openmaps.data.weather

import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.RainRadar
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.WeatherWarnings
import org.koin.dsl.module

/** A server speaking the Rain Viewer v2 API for the radar layer and its forecast. */
val RainRadarServer =
    ServiceSetting(
        "server.radar",
        ServiceRole.RAIN_RADAR,
        ServiceSettingKind.SERVER_URL,
        "LibreWXR",
        default = LIBREWXR,
        helpUrl = "https://github.com/JoshuaKimsey/LibreWXR",
    )

/** A LibreWXR server for official weather warnings. */
val WeatherWarningsServer =
    ServiceSetting(
        "server.warnings",
        ServiceRole.WEATHER_WARNINGS,
        ServiceSettingKind.SERVER_URL,
        "LibreWXR",
        default = LIBREWXR,
        helpUrl = "https://github.com/JoshuaKimsey/LibreWXR/blob/main/docs/alerts.md",
    )

internal const val LIBREWXR = "https://api.librewxr.net"

/** The settings this module's sources read. */
val weatherServiceSettings = listOf(RainRadarServer, WeatherWarningsServer)

val weatherModule =
    module {
        single<RainRadar> {
            val settings = get<SettingsStore>()
            RainViewerRadar(get(), settings.serviceValue(RainRadarServer.id).map { it ?: LIBREWXR }, settings.radarOptions)
        }
        single<WeatherWarnings> {
            LibreWxrWarnings(get(), get<SettingsStore>().serviceValue(WeatherWarningsServer.id).map { it ?: LIBREWXR })
        }
    }
