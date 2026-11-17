package nz.eloque.openmaps.data.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nz.eloque.openmaps.core.model.TripWatchStore
import nz.eloque.openmaps.core.model.WatchedLeg
import nz.eloque.openmaps.core.model.WatchedStop
import nz.eloque.openmaps.core.model.WatchedTrip
import kotlin.time.Instant

internal class RoomTripWatchStore(
    private val dao: TripWatchDao,
) : TripWatchStore {
    override val trips: Flow<List<WatchedTrip>> =
        combine(dao.trips(), dao.legs()) { trips, legs ->
            val byTrip = legs.groupBy { it.watchId }
            trips.mapNotNull { it.toModel(byTrip[it.id].orEmpty()) }
        }

    override suspend fun trip(id: String): WatchedTrip? = dao.trip(id)?.toModel(dao.legs(id))

    override suspend fun save(trip: WatchedTrip) {
        dao.save(
            WatchedTripEntity(trip.id, trip.destination, trip.checkedAt.toEpochMilliseconds()),
            trip.legs.mapIndexed { position, leg -> leg.toEntity(trip.id, position) },
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)
}

private fun WatchedTripEntity.toModel(legs: List<WatchedLegEntity>): WatchedTrip? =
    legs.takeIf { it.isNotEmpty() }?.let { rows ->
        WatchedTrip(id, destination, rows.sortedBy { it.position }.map { it.toModel() }, Instant.fromEpochMilliseconds(checkedAt))
    }

private fun WatchedLegEntity.toModel() =
    WatchedLeg(
        tripId = tripId,
        line = line,
        from = stop(fromName, fromStopId, fromScheduled, fromExpected, fromPlatform, fromCancelled),
        to = stop(toName, toStopId, toScheduled, toExpected, toPlatform, toCancelled),
        cancelled = cancelled,
    )

private fun stop(
    name: String,
    stopId: String?,
    scheduled: Long,
    expected: Long,
    platform: String?,
    cancelled: Boolean,
) = WatchedStop(name, stopId, Instant.fromEpochMilliseconds(scheduled), Instant.fromEpochMilliseconds(expected), platform, cancelled)

private fun WatchedLeg.toEntity(
    watchId: String,
    position: Int,
) = WatchedLegEntity(
    watchId = watchId,
    position = position,
    tripId = tripId,
    line = line,
    cancelled = cancelled,
    fromName = from.name,
    fromStopId = from.stopId,
    fromScheduled = from.scheduled.toEpochMilliseconds(),
    fromExpected = from.expected.toEpochMilliseconds(),
    fromPlatform = from.platform,
    fromCancelled = from.cancelled,
    toName = to.name,
    toStopId = to.stopId,
    toScheduled = to.scheduled.toEpochMilliseconds(),
    toExpected = to.expected.toEpochMilliseconds(),
    toPlatform = to.platform,
    toCancelled = to.cancelled,
)
