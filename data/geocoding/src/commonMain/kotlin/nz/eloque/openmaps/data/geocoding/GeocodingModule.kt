package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.module

const val NOMINATIM_API = "https://nominatim.openstreetmap.org"

/** Uses the server from [SettingsStore.placeDetailsUrl] when one is set, otherwise the public Nominatim. */
val geocodingModule =
    module {
        single<PlaceDetailsSource> {
            val settings = get<SettingsStore>()
            NominatimPlaceDetails(get()) { settings.placeDetailsUrl.value ?: NOMINATIM_API }
        }
    }
