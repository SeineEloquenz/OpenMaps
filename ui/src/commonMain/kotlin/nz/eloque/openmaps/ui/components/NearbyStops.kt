package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.TransitRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class NearbyStop(
    val id: String?,
    val name: String,
    val location: LatLng,
    val groups: List<DepartureGroup>,
)

/** The stops around [location] with departures in the next two hours, nearest first. */
suspend fun TransitRepository.nearbyStops(
    location: LatLng,
    maxStops: Int,
    maxLines: Int = 5,
): List<NearbyStop> {
    val latest = Clock.System.now() + 2.hours
    return departuresNear(location, radiusMeters = 500, count = 60)
        .departures
        .filter { it.time.actual <= latest }
        .groupBy { it.stopName }
        .values
        .sortedBy { it.first().stopLocation.distanceTo(location) }
        .take(maxStops)
        .map { departures ->
            val first = departures.first()
            NearbyStop(first.stopId, first.stopName, first.stopLocation, groupDepartures(departures).take(maxLines))
        }
}

@Composable
fun NearbyStopCard(
    stop: NearbyStop,
    now: Instant,
    onClick: () -> Unit,
    onOpenTrip: (tripId: String, stopName: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stop.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        stop.groups.forEach { DepartureGroupRow(it, now, onOpenTrip = { onOpenTrip(it, stop.name) }) }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
