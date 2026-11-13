package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.load_failed
import nz.eloque.openmaps.ui.resources.retry
import org.jetbrains.compose.resources.stringResource

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data object Failed : LoadState<Nothing>

    data class Loaded<T>(
        val value: T,
    ) : LoadState<T>
}

@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading ->
            CircularProgressIndicator(modifier.padding(24.dp))
        LoadState.Failed ->
            Column(
                modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            }
        is LoadState.Loaded -> content(state.value)
    }
}
