package nz.eloque.openmaps.core.model

import kotlin.time.Instant

/** A pump price as a station last reported it. */
data class FuelPrice(
    val fuel: Fuel,
    val price: Double,
    /** ISO 4217 code, such as `EUR`. */
    val currency: String,
    /** Null when the source does not say. */
    val updated: Instant?,
)

/** A station as a price source knows it. */
data class PricedStation(
    val location: LatLng,
    val name: String?,
    val brand: String?,
    val address: String?,
    val prices: List<FuelPrice>,
    val source: Attribution,
)

/** One source of pump prices, such as a country's price register, combined with others by [FuelPrices]. */
interface FuelPriceSource {
    val attribution: Attribution

    /** Where the source has stations. Areas outside these are never asked for. */
    val coverage: List<GeoBounds>

    /** False until what the source needs, such as an API key, is set. */
    val available: Boolean get() = true

    /** Stations with prices in [area], nearest to [center] first. Some sources only know the cheapest ones. */
    suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation>
}

/** Pump prices from every source covering an area. */
interface FuelPrices {
    /** Stations with prices in [area]. Sources that fail are left out, so this is empty where none could answer. */
    suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation>
}
