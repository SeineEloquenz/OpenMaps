package nz.eloque.openmaps.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.ui.components.Page
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.about
import nz.eloque.openmaps.ui.resources.about_app
import nz.eloque.openmaps.ui.resources.about_licence
import nz.eloque.openmaps.ui.resources.data_sources
import nz.eloque.openmaps.ui.resources.privacy
import nz.eloque.openmaps.ui.resources.privacy_detail
import nz.eloque.openmaps.ui.resources.source_map
import nz.eloque.openmaps.ui.resources.source_map_detail
import nz.eloque.openmaps.ui.resources.source_places
import nz.eloque.openmaps.ui.resources.source_places_detail
import nz.eloque.openmaps.ui.resources.source_tiles
import nz.eloque.openmaps.ui.resources.source_tiles_detail
import nz.eloque.openmaps.ui.resources.source_transit
import nz.eloque.openmaps.ui.resources.source_transit_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutPage(
    version: String,
    onBack: () -> Unit,
) = Page(stringResource(Res.string.about), onBack) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
        Text(stringResource(Res.string.about_app, version), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.about_licence), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle(Res.string.data_sources)
        SourceRow(Res.string.source_transit, Res.string.source_transit_detail, "https://transitous.org/sources/")
        SourceRow(Res.string.source_map, Res.string.source_map_detail, "https://www.openstreetmap.org/copyright")
        SourceRow(Res.string.source_tiles, Res.string.source_tiles_detail, "https://openfreemap.org")
        SourceRow(Res.string.source_places, Res.string.source_places_detail, "https://nominatim.org")
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle(Res.string.privacy)
        Text(stringResource(Res.string.privacy_detail), modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SourceRow(
    title: StringResource,
    detail: StringResource,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(detail)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { uriHandler.openUri(url) },
    )
}
