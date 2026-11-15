package nz.eloque.openmaps.ui

import nz.eloque.openmaps.ui.category.CategoryViewModel
import nz.eloque.openmaps.ui.directions.DirectionsViewModel
import nz.eloque.openmaps.ui.directions.ItineraryCache
import nz.eloque.openmaps.ui.directions.TripViewModel
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
        viewModel { params -> PlaceViewModel(get(), get(), get(), params.getOrNull(), params.get(), params.get()) }
        single { ItineraryCache() }
        viewModel { params -> DirectionsViewModel(get(), get(), get(), params.get()) }
        viewModel { params -> TripViewModel(get(), params.get()) }
        viewModel { params -> CategoryViewModel(get(), params.get()) }
    }
