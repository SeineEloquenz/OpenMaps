package nz.eloque.openmaps.ui.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.DirectionsButton
import nz.eloque.openmaps.ui.components.FocusOnMap
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.SaveButton
import nz.eloque.openmaps.ui.navigation.PlaceKey
import nz.eloque.openmaps.ui.navigation.parsedOsmId
import nz.eloque.openmaps.ui.navigation.toSearchResult
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.phone
import nz.eloque.openmaps.ui.resources.website
import nz.eloque.openmaps.ui.resources.wheelchair
import nz.eloque.openmaps.ui.resources.wheelchair_limited
import nz.eloque.openmaps.ui.resources.wheelchair_no
import nz.eloque.openmaps.ui.resources.wheelchair_yes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlaceSheet(
    place: PlaceKey,
    map: MapController,
    onDirections: () -> Unit,
) {
    val viewModel = koinViewModel<PlaceViewModel> { parametersOf(place.parsedOsmId) }
    val details by viewModel.details.collectAsStateWithLifecycle()
    FocusOnMap(map, place.location, zoomIn = !place.pinned)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(place.name, style = MaterialTheme.typography.headlineSmall)
        val subtitle = listOfNotNull(place.category?.toCategoryLabel(), place.subtitle).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionsButton(onDirections)
            SaveButton(place.toSearchResult())
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
        LoadStateContent(details, onRetry = viewModel::load) { it?.let { DetailRows(it) } }
    }
}

@Composable
private fun DetailRows(details: PlaceDetails) {
    val uriHandler = LocalUriHandler.current
    details.openingHours?.let { OpeningHoursRow(it) }
    details.website?.let { url ->
        DetailRow(Icons.Default.Language, stringResource(Res.string.website), url) { uriHandler.openUri(url) }
    }
    details.phone?.let { phone ->
        DetailRow(Icons.Default.Phone, stringResource(Res.string.phone), phone) {
            uriHandler.openUri("tel:${phone.filter { c -> c.isDigit() || c == '+' }}")
        }
    }
    details.wheelchair?.let { value ->
        val label =
            when (value) {
                "yes", "designated" -> stringResource(Res.string.wheelchair_yes)
                "limited" -> stringResource(Res.string.wheelchair_limited)
                "no" -> stringResource(Res.string.wheelchair_no)
                else -> null
            }
        label?.let { DetailRow(Icons.AutoMirrored.Filled.Accessible, stringResource(Res.string.wheelchair), it) }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(value) },
        overlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** Turns an OSM tag value such as `fast_food`, or a MOTIS icon name such as `museum_16`, into a label. */
fun String.toCategoryLabel(): String = replace(IconSizeSuffix, "").replace('_', ' ').replaceFirstChar { it.uppercase() }

private val IconSizeSuffix = Regex("_\\d+$")
