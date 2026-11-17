package nz.eloque.openmaps.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cancel
import nz.eloque.openmaps.ui.resources.list_icon
import nz.eloque.openmaps.ui.resources.list_name
import nz.eloque.openmaps.ui.resources.ok
import org.jetbrains.compose.resources.stringResource

/** Asks for a list's name and icon. */
@Composable
fun ListDialog(
    initialName: String,
    initialIcon: ListIcon,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: ListIcon) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.list_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.list_icon), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ListIcon.entries.forEach { option ->
                        FilledIconToggleButton(checked = option == icon, onCheckedChange = { icon = option }) {
                            Icon(option.vector, contentDescription = option.name.lowercase())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), icon) }) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
