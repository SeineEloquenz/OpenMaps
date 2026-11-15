package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.NearbyPlaces
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PlaceMediaSource
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.module

const val NOMINATIM_API = "https://nominatim.openstreetmap.org"
const val PHOTON_API = "https://photon.komoot.io"

/** OpenMaps' own Open Charge Map key, shared by everyone who has not entered one. It is public with the source code. */
private const val OPEN_CHARGE_MAP_APP_KEY = "9fb1fe48-c6ed-4c88-9f65-03e9fee7afc9"

/** Uses the servers from [SettingsStore] when set, otherwise the public Nominatim and Photon. Media always comes from Wikimedia. */
fun geocodingModule(language: () -> String? = { null }) =
    module {
        single {
            val settings = get<SettingsStore>()
            NominatimPlaceDetails(get()) { settings.placeDetailsUrl.value ?: NOMINATIM_API }
        }
        single {
            val settings = get<SettingsStore>()
            OpenChargeMapStations(get()) { settings.openChargeMapKey.value ?: OPEN_CHARGE_MAP_APP_KEY }
        }
        single<PlaceDetailsSource> { CombinedPlaceDetails(listOf(get<NominatimPlaceDetails>(), get<OpenChargeMapStations>())) }
        single<NearbyPlaces> {
            val settings = get<SettingsStore>()
            CompositeNearbyPlaces(
                candidates = PhotonCategorySearch(get(), language) { settings.categorySearchUrl.value ?: PHOTON_API },
                details = get<NominatimPlaceDetails>(),
                charging = get<OpenChargeMapStations>(),
            )
        }
        single<PlaceMediaSource> { WikimediaMedia(get(), language) }
    }
