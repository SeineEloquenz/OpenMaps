package nz.eloque.openmaps.core.model

import kotlin.time.Instant

/** A transit vehicle's run from one stop to the next along [path]. */
data class VehicleRun(
    val tripId: String,
    val name: String,
    val mode: TransportMode,
    val color: Long?,
    val nextStop: String,
    val departure: Instant,
    val arrival: Instant,
    val path: List<LatLng>,
    /** True when the times include live delays rather than only the timetable. */
    val realTime: Boolean,
) {
    private val distances: DoubleArray by lazy {
        DoubleArray(path.size).also { sums ->
            for (i in 1 until path.size) sums[i] = sums[i - 1] + path[i - 1].distanceTo(path[i])
        }
    }

    /** Where the vehicle is at [time], assuming an even speed between the stops, or null when it is not on this run then. */
    fun positionAt(time: Instant): LatLng? {
        if (path.isEmpty() || time < departure || time > arrival) return null
        val duration = (arrival - departure).inWholeMilliseconds
        val share = if (duration <= 0) 1.0 else (time - departure).inWholeMilliseconds.toDouble() / duration
        val target = distances.last() * share
        val index = distances.indexOfFirst { it >= target }.takeIf { it > 0 } ?: return path.first()
        val from = path[index - 1]
        val to = path[index]
        val length = distances[index] - distances[index - 1]
        val t = if (length <= 0) 0.0 else (target - distances[index - 1]) / length
        return LatLng(from.latitude + (to.latitude - from.latitude) * t, from.longitude + (to.longitude - from.longitude) * t)
    }
}

interface LiveVehicles {
    /** The runs crossing [bounds] at some point between [from] and [until]. [zoom] lets the source leave out minor ones. */
    suspend fun runs(
        bounds: GeoBounds,
        zoom: Int,
        from: Instant,
        until: Instant,
    ): List<VehicleRun>
}
