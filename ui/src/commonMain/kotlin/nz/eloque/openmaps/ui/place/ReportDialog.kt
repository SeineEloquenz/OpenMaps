package nz.eloque.openmaps.ui.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MapNotes
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cancel
import nz.eloque.openmaps.ui.resources.ok
import nz.eloque.openmaps.ui.resources.report_explanation
import nz.eloque.openmaps.ui.resources.report_failed
import nz.eloque.openmaps.ui.resources.report_hint
import nz.eloque.openmaps.ui.resources.report_send
import nz.eloque.openmaps.ui.resources.report_sent
import nz.eloque.openmaps.ui.resources.report_title
import nz.eloque.openmaps.ui.resources.report_view
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private sealed interface ReportState {
    data object Editing : ReportState

    data object Sending : ReportState

    data class Sent(
        val link: String,
    ) : ReportState

    data object Failed : ReportState
}

/** Asks what is wrong at [location] and publishes it as a map note, prefixed with [placeName]. */
@Composable
fun ReportDialog(
    location: LatLng,
    placeName: String?,
    onDismiss: () -> Unit,
) {
    val notes = koinInject<MapNotes>()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<ReportState>(ReportState.Editing) }
    val current = state
    AlertDialog(
        onDismissRequest = { if (current != ReportState.Sending) onDismiss() },
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (current) {
                    is ReportState.Sent -> Text(stringResource(Res.string.report_sent))
                    ReportState.Sending -> CircularProgressIndicator()
                    else -> {
                        Text(stringResource(Res.string.report_explanation), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(stringResource(Res.string.report_hint)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (current == ReportState.Failed) {
                            Text(stringResource(Res.string.report_failed), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (current) {
                is ReportState.Sent -> TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ok)) }
                ReportState.Sending -> Unit
                else ->
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            state = ReportState.Sending
                            scope.launch {
                                state =
                                    try {
                                        val body = placeName?.let { "$it: ${text.trim()}" } ?: text.trim()
                                        ReportState.Sent(notes.report(location, body))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        ReportState.Failed
                                    }
                            }
                        },
                    ) { Text(stringResource(Res.string.report_send)) }
            }
        },
        dismissButton = {
            when (current) {
                is ReportState.Sent ->
                    TextButton(onClick = { uriHandler.openUri(current.link) }) { Text(stringResource(Res.string.report_view)) }
                ReportState.Sending -> Unit
                else -> TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}
