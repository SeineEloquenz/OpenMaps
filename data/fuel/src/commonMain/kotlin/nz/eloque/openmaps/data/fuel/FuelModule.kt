package nz.eloque.openmaps.data.fuel

import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.FuelPrices
import org.koin.dsl.bind
import org.koin.dsl.module

/** Registers each country's source as a [FuelPriceSource]. Other modules add countries by registering more. */
val fuelModule =
    module {
        single { FrancePrices(get()) } bind FuelPriceSource::class
        single { AustriaPrices(get()) } bind FuelPriceSource::class
        single { TankPulsPrices(get()) } bind FuelPriceSource::class
        single<FuelPrices> { CombinedFuelPrices(getAll()) }
    }
