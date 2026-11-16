package nz.eloque.openmaps.data.fuel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.FuelPrices
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PricedStation

/** Asks the available [sources] covering an area in parallel and joins their stations, nearest first. */
internal class CombinedFuelPrices(
    private val sources: List<FuelPriceSource>,
) : FuelPrices {
    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> =
        coroutineScope {
            sources
                .filter { source -> source.available && source.coverage.any { it.intersects(area) } }
                .map { source -> async { source.stationsOrEmpty(center, area) } }
                .awaitAll()
                .flatten()
                .sortedBy { it.location.distanceTo(center) }
        }
}

private suspend fun FuelPriceSource.stationsOrEmpty(
    center: LatLng,
    area: GeoBounds,
): List<PricedStation> =
    try {
        stations(center, area)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
