package nz.eloque.openmaps.map

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A map button, described by [label] for screen readers. */
data class MapAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
internal fun MapActionButton(
    action: MapAction,
    modifier: Modifier = Modifier,
) {
    // Kept clear of the compass even while it is hidden, so the button does not jump when the map turns north.
    SmallFloatingActionButton(onClick = action.onClick, modifier = modifier.padding(top = CompassSize + 8.dp)) {
        Icon(action.icon, contentDescription = action.label)
    }
}

private val CompassSize = 48.dp
