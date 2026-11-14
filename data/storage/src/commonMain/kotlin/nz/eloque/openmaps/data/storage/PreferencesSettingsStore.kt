package nz.eloque.openmaps.data.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.WalkingSpeed

/** Values this version does not know, such as a mode group added later, are ignored on read. */
internal class PreferencesSettingsStore(
    private val settings: Settings,
) : SettingsStore {
    private val _routeOptions = MutableStateFlow(readRouteOptions())
    override val routeOptions: StateFlow<RouteOptions> = _routeOptions.asStateFlow()

    override fun setRouteOptions(options: RouteOptions) {
        settings.putString(MODES, options.modes.joinToString(",") { it.name })
        settings.putInt(MAX_TRANSFERS, options.maxTransfers ?: NO_LIMIT)
        settings.putString(WALKING_SPEED, options.walkingSpeed.name)
        settings.putBoolean(WHEELCHAIR, options.wheelchair)
        _routeOptions.value = options
    }

    private fun readRouteOptions(): RouteOptions {
        val defaults = RouteOptions()
        val modes =
            settings
                .getStringOrNull(MODES)
                ?.split(',')
                ?.mapNotNull { name -> ModeGroup.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
        return RouteOptions(
            modes = modes ?: defaults.modes,
            maxTransfers = settings.getInt(MAX_TRANSFERS, NO_LIMIT).takeIf { it >= 0 },
            walkingSpeed =
                WalkingSpeed.entries.firstOrNull { it.name == settings.getStringOrNull(WALKING_SPEED) } ?: defaults.walkingSpeed,
            wheelchair = settings.getBoolean(WHEELCHAIR, defaults.wheelchair),
        )
    }

    private companion object {
        const val MODES = "route.modes"
        const val MAX_TRANSFERS = "route.maxTransfers"
        const val WALKING_SPEED = "route.walkingSpeed"
        const val WHEELCHAIR = "route.wheelchair"
        const val NO_LIMIT = -1
    }
}
