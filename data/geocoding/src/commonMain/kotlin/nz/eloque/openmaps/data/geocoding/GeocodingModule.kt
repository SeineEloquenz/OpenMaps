package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.PlaceDetailsSource
import org.koin.dsl.module

const val NOMINATIM_API = "https://nominatim.openstreetmap.org"

fun geocodingModule(nominatimUrl: String = NOMINATIM_API) =
    module {
        single<PlaceDetailsSource> { NominatimPlaceDetails(get(), nominatimUrl) }
    }
