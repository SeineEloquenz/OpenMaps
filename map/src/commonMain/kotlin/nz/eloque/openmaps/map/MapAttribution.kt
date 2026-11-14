package nz.eloque.openmaps.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.attributions

/** A faint info button that expands to the map data attribution and collapses when the map is panned. */
@Composable
internal fun MapAttribution(modifier: Modifier = Modifier) {
    val mapState = checkNotNull(LocalMapState.current)
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(mapState) {
        snapshotFlow { mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.Gesture }
            .filter { it }
            .collect { expanded = false }
    }
    val attributions by remember(mapState) { derivedStateOf { mapState.style.attributions() } }
    if (attributions.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (expanded) colors.surface.copy(alpha = 0.9f) else Color.Transparent)
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .pointerInput(Unit) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(expanded, enter = expandHorizontally(), exit = shrinkHorizontally()) {
            AttributionLinks(
                attributions,
                textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = AttributionDefaults.contentDescription(),
                tint = colors.onSurface.copy(alpha = if (expanded) 0.8f else 0.45f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
