package nz.eloque.openmaps.ui

import nz.eloque.openmaps.ui.directions.DirectionsViewModel
import nz.eloque.openmaps.ui.directions.ItineraryCache
import nz.eloque.openmaps.ui.home.HomeViewModel
import nz.eloque.openmaps.ui.place.PlaceViewModel
import nz.eloque.openmaps.ui.search.SearchViewModel
import nz.eloque.openmaps.ui.stop.StopViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule =
    module {
        viewModelOf(::HomeViewModel)
        viewModelOf(::SearchViewModel)
        viewModel { params -> StopViewModel(get(), params.get()) }
        viewModel { params -> PlaceViewModel(get(), params.getOrNull()) }
        single { ItineraryCache() }
        viewModel { params -> DirectionsViewModel(get(), get(), params.get()) }
    }
