package nz.eloque.openmaps.ui.roads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.RoadEvent
import nz.eloque.openmaps.core.model.RoadEventKind
import nz.eloque.openmaps.core.model.RoadEvents
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDate
import nz.eloque.openmaps.ui.navigation.RoadEventKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.road_closed
import nz.eloque.openmaps.ui.resources.road_event_approximate
import nz.eloque.openmaps.ui.resources.road_event_from
import nz.eloque.openmaps.ui.resources.road_event_kind_accident
import nz.eloque.openmaps.ui.resources.road_event_kind_conditions
import nz.eloque.openmaps.ui.resources.road_event_kind_congestion
import nz.eloque.openmaps.ui.resources.road_event_kind_obstruction
import nz.eloque.openmaps.ui.resources.road_event_kind_other
import nz.eloque.openmaps.ui.resources.road_event_kind_restriction
import nz.eloque.openmaps.ui.resources.road_event_kind_roadworks
import nz.eloque.openmaps.ui.resources.road_event_lanes
import nz.eloque.openmaps.ui.resources.road_event_period
import nz.eloque.openmaps.ui.resources.road_event_source
import nz.eloque.openmaps.ui.resources.road_event_speed_limit
import nz.eloque.openmaps.ui.resources.road_event_unconfirmed
import nz.eloque.openmaps.ui.resources.road_event_until
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Instant

/** A tapped roadworks site, closure or other road event. */
@Composable
fun RoadEventSheet(key: RoadEventKey) {
    val event = koinInject<RoadEvents>().event(key.properties) ?: return
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(event.color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(event.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(event.title), style = MaterialTheme.typography.headlineSmall)
                event.road?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            }
        }
        period(event)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        val restrictions =
            listOfNotNull(
                stringResource(Res.string.road_event_lanes).takeIf { event.lanesRestricted && !event.closed },
                event.speedLimit?.let { stringResource(Res.string.road_event_speed_limit, it) },
            )
        if (restrictions.isNotEmpty()) Text(restrictions.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        event.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (!event.exactGeometry) Note(stringResource(Res.string.road_event_approximate))
        Text(
            stringResource(Res.string.road_event_source, event.attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!event.licenceConfirmed) Note(stringResource(Res.string.road_event_unconfirmed))
    }
}

@Composable
private fun Note(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun period(event: RoadEvent): String? {
    val from = event.validFrom?.let(::dateTime)
    val until = event.validUntil?.let(::dateTime)
    return when {
        from != null && until != null -> stringResource(Res.string.road_event_period, from, until)
        until != null -> stringResource(Res.string.road_event_until, until)
        from != null -> stringResource(Res.string.road_event_from, from)
        else -> null
    }
}

private fun dateTime(time: Instant): String =
    "${formatDate(time.toLocalDateTime(TimeZone.currentSystemDefault()).date)}, ${clockTime(time)}"

val RoadEvent.color: Color
    get() = if (closed) ClosedColor else WorksColor

val RoadEvent.icon: ImageVector
    get() = if (closed) Icons.Default.Block else Icons.Default.Construction

private val RoadEvent.title: StringResource
    get() =
        if (closed) {
            Res.string.road_closed
        } else {
            when (kind) {
                RoadEventKind.ROADWORKS -> Res.string.road_event_kind_roadworks
                RoadEventKind.ACCIDENT -> Res.string.road_event_kind_accident
                RoadEventKind.OBSTRUCTION -> Res.string.road_event_kind_obstruction
                RoadEventKind.CONDITIONS -> Res.string.road_event_kind_conditions
                RoadEventKind.CONGESTION -> Res.string.road_event_kind_congestion
                RoadEventKind.RESTRICTION -> Res.string.road_event_kind_restriction
                RoadEventKind.OTHER -> Res.string.road_event_kind_other
            }
        }

private val ClosedColor = Color(0xFFD32F2F)
private val WorksColor = Color(0xFFF57C00)
