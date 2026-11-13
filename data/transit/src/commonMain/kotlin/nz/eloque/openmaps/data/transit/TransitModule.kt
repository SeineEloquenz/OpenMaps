package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.TransitRepository
import org.koin.dsl.module

const val TRANSITOUS_API = "https://api.transitous.org/api"

fun transitModule(
    baseUrl: String = TRANSITOUS_API,
    language: () -> String? = { null },
) = module {
    single<TransitRepository> { MotisTransitRepository(get(), baseUrl) }
    single<Geocoder> { MotisGeocoder(get(), baseUrl, language) }
}
