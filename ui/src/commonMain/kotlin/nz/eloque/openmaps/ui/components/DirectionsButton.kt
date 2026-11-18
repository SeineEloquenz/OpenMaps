package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.directions
import org.jetbrains.compose.resources.stringResource

@Composable
fun DirectionsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(onClick = onClick, modifier = modifier.size(ACTION_SIZE)) {
        Icon(Icons.Default.Directions, contentDescription = stringResource(Res.string.directions))
    }
}

/** The size of the round action buttons on place and stop sheets. */
val ACTION_SIZE = 48.dp
