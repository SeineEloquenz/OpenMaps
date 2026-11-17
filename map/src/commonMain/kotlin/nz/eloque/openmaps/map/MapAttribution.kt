package nz.eloque.openmaps.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.attributions

/** A faint info button that hands the attribution of every source on the map to [onClick]. */
@Composable
internal fun MapAttribution(
    onClick: ((List<String>) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val mapState = checkNotNull(LocalMapState.current)
    val attributions by remember(mapState) { derivedStateOf { mapState.style.attributions() } }
    if (attributions.isEmpty() || onClick == null) return
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { onClick(attributions) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = AttributionDefaults.contentDescription(),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}
