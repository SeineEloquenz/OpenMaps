package nz.eloque.openmaps.data.fuel

import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.FuelPrices
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.bind
import org.koin.dsl.module

/** An open-fuel-prices server for the countries that publish whole-country price files. */
val FuelPricesServer =
    ServiceSetting(
        "server.fuel",
        ServiceRole.FUEL_PRICES,
        ServiceSettingKind.SERVER_URL,
        "open-fuel-prices",
        default = "https://ori.eloque.nz/fuel",
        helpUrl = "https://github.com/SeineEloquenz/open-fuel-prices",
    )

/** The settings this module's sources read. */
val fuelServiceSettings = listOf(FuelPricesServer)

/** Registers each source as a [FuelPriceSource]. Other modules add countries by registering more. */
val fuelModule =
    module {
        single { OriFuelPrices(get(), get<SettingsStore>().serviceValue(FuelPricesServer.id)) } bind FuelPriceSource::class
        single { AustriaPrices(get()) } bind FuelPriceSource::class
        single { TankPulsPrices(get()) } bind FuelPriceSource::class
        single<FuelPrices> { CombinedFuelPrices(getAll()) }
    }
