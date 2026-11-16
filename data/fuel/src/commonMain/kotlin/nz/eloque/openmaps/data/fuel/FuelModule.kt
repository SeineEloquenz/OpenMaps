package nz.eloque.openmaps.data.fuel

import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.FuelPrices
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.bind
import org.koin.dsl.module

internal val TankerkoenigKey =
    ServiceSetting(
        "keys.tankerkoenig",
        ServiceRole.FUEL_PRICES,
        ServiceSettingKind.API_KEY,
        "Tankerkönig",
        default = null,
        helpUrl = "https://creativecommons.tankerkoenig.de",
    )

val fuelServiceSettings = listOf(TankerkoenigKey)

/** Registers each country's source as a [FuelPriceSource]. Other modules add countries by registering more. */
val fuelModule =
    module {
        single { FrancePrices(get()) } bind FuelPriceSource::class
        single { AustriaPrices(get()) } bind FuelPriceSource::class
        single {
            val settings = get<SettingsStore>()
            TankerkoenigPrices(get()) { settings.serviceValue(TankerkoenigKey.id).value }
        } bind FuelPriceSource::class
        single<FuelPrices> { CombinedFuelPrices(getAll()) }
    }
