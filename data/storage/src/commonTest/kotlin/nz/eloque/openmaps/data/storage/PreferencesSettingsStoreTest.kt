package nz.eloque.openmaps.data.storage

import com.russhwolf.settings.MapSettings
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesSettingsStoreTest {
    @Test
    fun defaultsWhenEmpty() {
        assertEquals(RouteOptions(), PreferencesSettingsStore(MapSettings()).routeOptions.value)
    }

    @Test
    fun remembersTheTravelMode() {
        val settings = MapSettings()
        assertEquals(TravelMode.TRANSIT, PreferencesSettingsStore(settings).travelMode.value)
        PreferencesSettingsStore(settings).setTravelMode(TravelMode.BIKE)
        assertEquals(TravelMode.BIKE, PreferencesSettingsStore(settings).travelMode.value)
    }

    @Test
    fun roundTripsRouteOptions() {
        val settings = MapSettings()
        val options = RouteOptions(setOf(ModeGroup.BUS, ModeGroup.FERRY), maxTransfers = 1, WalkingSpeed.SLOW, wheelchair = true)
        PreferencesSettingsStore(settings).setRouteOptions(options)
        assertEquals(options, PreferencesSettingsStore(settings).routeOptions.value)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        val settings = MapSettings("route.modes" to "BUS,HOVERCRAFT", "route.walkingSpeed" to "WARP")
        val options = PreferencesSettingsStore(settings).routeOptions.value
        assertEquals(setOf(ModeGroup.BUS), options.modes)
        assertEquals(WalkingSpeed.NORMAL, options.walkingSpeed)
    }
}
