package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.StreetRequest
import nz.eloque.openmaps.core.model.StreetRouter
import nz.eloque.openmaps.core.model.TransitRepository

/** Street routes from the MOTIS server's direct connections. */
class TransitStreetRouter internal constructor(
    private val transit: TransitRepository,
) : StreetRouter {
    override suspend fun route(request: StreetRequest): List<Itinerary> =
        transit
            .plan(
                PlanRequest(
                    from = PlanTarget.Coordinates(request.from),
                    to = PlanTarget.Coordinates(request.to),
                    time = request.time,
                    arriveBy = request.arriveBy,
                    options = request.options,
                    directModes = request.modes,
                    withTransit = false,
                ),
            ).direct
            .filter { it.legs.isNotEmpty() }
            .distinctBy { it.legs.first().mode }
}
