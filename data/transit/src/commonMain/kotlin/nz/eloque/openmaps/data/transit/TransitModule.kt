package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransitRepository
import org.koin.dsl.module

const val TRANSITOUS_API = "https://api.transitous.org/api"

/** Uses the server from [SettingsStore.transitUrl] when one is set, otherwise Transitous. */
fun transitModule(language: () -> String? = { null }) =
    module {
        single<TransitRepository> {
            val settings = get<SettingsStore>()
            MotisTransitRepository(get()) { settings.transitUrl.value ?: TRANSITOUS_API }
        }
        single<Geocoder> {
            val settings = get<SettingsStore>()
            MotisGeocoder(get(), { settings.transitUrl.value ?: TRANSITOUS_API }, language)
        }
    }
