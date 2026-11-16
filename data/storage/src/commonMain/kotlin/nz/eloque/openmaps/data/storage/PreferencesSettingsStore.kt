package nz.eloque.openmaps.data.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TravelMode
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
        settings.putBoolean(SHARED_VEHICLES, options.sharedVehicles)
        _routeOptions.value = options
    }

    private val _travelMode =
        MutableStateFlow(TravelMode.entries.firstOrNull { it.name == settings.getStringOrNull(TRAVEL_MODE) } ?: TravelMode.TRANSIT)
    override val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    override fun setTravelMode(mode: TravelMode) {
        settings.putString(TRAVEL_MODE, mode.name)
        _travelMode.value = mode
    }

    private val _distanceUnit =
        MutableStateFlow(DistanceUnit.entries.firstOrNull { it.name == settings.getStringOrNull(DISTANCE_UNIT) } ?: DistanceUnit.METRIC)
    override val distanceUnit: StateFlow<DistanceUnit> = _distanceUnit.asStateFlow()

    override fun setDistanceUnit(unit: DistanceUnit) {
        settings.putString(DISTANCE_UNIT, unit.name)
        _distanceUnit.value = unit
    }

    private val _speedingAlert = MutableStateFlow(settings.getBoolean(SPEEDING_ALERT, false))
    override val speedingAlert: StateFlow<Boolean> = _speedingAlert.asStateFlow()

    override fun setSpeedingAlert(enabled: Boolean) {
        settings.putBoolean(SPEEDING_ALERT, enabled)
        _speedingAlert.value = enabled
    }

    private val serviceValues = mutableMapOf<String, MutableStateFlow<String?>>()

    override fun serviceValue(id: String): StateFlow<String?> = serviceState(id).asStateFlow()

    override fun setServiceValue(
        id: String,
        value: String?,
    ) {
        val cleaned = value?.trim()?.takeIf { it.isNotEmpty() }
        if (cleaned == null) settings.remove(id) else settings.putString(id, cleaned)
        serviceState(id).value = cleaned
    }

    private fun serviceState(id: String) = serviceValues.getOrPut(id) { MutableStateFlow(settings.getStringOrNull(id)) }

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
            sharedVehicles = settings.getBoolean(SHARED_VEHICLES, defaults.sharedVehicles),
        )
    }

    private companion object {
        const val MODES = "route.modes"
        const val MAX_TRANSFERS = "route.maxTransfers"
        const val WALKING_SPEED = "route.walkingSpeed"
        const val WHEELCHAIR = "route.wheelchair"
        const val SHARED_VEHICLES = "route.sharedVehicles"
        const val NO_LIMIT = -1
        const val TRAVEL_MODE = "route.travelMode"
        const val DISTANCE_UNIT = "units.distance"
        const val SPEEDING_ALERT = "navigation.speeding_alert"
    }
}
