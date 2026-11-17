package nz.eloque.openmaps.ui.layers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.HtmlStyle
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.close
import nz.eloque.openmaps.ui.resources.map_data
import nz.eloque.openmaps.ui.resources.map_data_detail
import org.jetbrains.compose.resources.stringResource

/** Who the data on the map comes from, each source with its links, in full. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDataSheet(
    attributions: List<String>,
    onDismiss: () -> Unit,
) {
    val links = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val texts =
        remember(attributions, links) {
            attributions.map { htmlToAnnotatedString(it, style = HtmlStyle(textLinkStyles = links)) }
        }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp)) {
            Text(stringResource(Res.string.map_data), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close)) }
        }
        Text(
            stringResource(Res.string.map_data_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(texts) { index, text ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}
