package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
    Button(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Directions, contentDescription = null)
        Text(stringResource(Res.string.directions), modifier = Modifier.padding(start = 8.dp))
    }
}
