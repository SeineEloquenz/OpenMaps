package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.CategorySearch
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.module

const val NOMINATIM_API = "https://nominatim.openstreetmap.org"
const val PHOTON_API = "https://photon.komoot.io"

/** Uses the servers from [SettingsStore] when set, otherwise the public Nominatim and Photon. */
fun geocodingModule(language: () -> String? = { null }) =
    module {
        single<PlaceDetailsSource> {
            val settings = get<SettingsStore>()
            NominatimPlaceDetails(get()) { settings.placeDetailsUrl.value ?: NOMINATIM_API }
        }
        single<CategorySearch> {
            val settings = get<SettingsStore>()
            PhotonCategorySearch(get(), language) { settings.categorySearchUrl.value ?: PHOTON_API }
        }
    }
