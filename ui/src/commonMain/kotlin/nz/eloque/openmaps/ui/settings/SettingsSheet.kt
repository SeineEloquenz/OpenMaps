package nz.eloque.openmaps.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.about
import nz.eloque.openmaps.ui.resources.server_category_search
import nz.eloque.openmaps.ui.resources.server_invalid
import nz.eloque.openmaps.ui.resources.server_place_details
import nz.eloque.openmaps.ui.resources.server_transit
import nz.eloque.openmaps.ui.resources.servers
import nz.eloque.openmaps.ui.resources.servers_detail
import nz.eloque.openmaps.ui.resources.settings
import nz.eloque.openmaps.ui.resources.settings_routes
import nz.eloque.openmaps.ui.resources.units
import nz.eloque.openmaps.ui.resources.units_imperial
import nz.eloque.openmaps.ui.resources.units_metric
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val DEFAULT_TRANSIT_URL = "https://api.transitous.org/api"
private const val DEFAULT_PLACE_DETAILS_URL = "https://nominatim.openstreetmap.org"
private const val DEFAULT_CATEGORY_SEARCH_URL = "https://photon.komoot.io"

@Composable
fun SettingsSheet(onOpenAbout: () -> Unit) {
    val settings = koinInject<SettingsStore>()
    val routeOptions by settings.routeOptions.collectAsStateWithLifecycle()
    val distanceUnit by settings.distanceUnit.collectAsStateWithLifecycle()
    val transitUrl by settings.transitUrl.collectAsStateWithLifecycle()
    val placeDetailsUrl by settings.placeDetailsUrl.collectAsStateWithLifecycle()
    val categorySearchUrl by settings.categorySearchUrl.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(Res.string.settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SectionTitle(Res.string.settings_routes)
        RouteOptionsEditor(routeOptions, settings::setRouteOptions)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle(Res.string.units)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = distanceUnit == DistanceUnit.METRIC,
                onClick = { settings.setDistanceUnit(DistanceUnit.METRIC) },
                label = { Text(stringResource(Res.string.units_metric)) },
            )
            FilterChip(
                selected = distanceUnit == DistanceUnit.IMPERIAL,
                onClick = { settings.setDistanceUnit(DistanceUnit.IMPERIAL) },
                label = { Text(stringResource(Res.string.units_imperial)) },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle(Res.string.servers)
        Text(
            stringResource(Res.string.servers_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ServerUrlField(Res.string.server_transit, transitUrl, DEFAULT_TRANSIT_URL, settings::setTransitUrl)
        ServerUrlField(Res.string.server_place_details, placeDetailsUrl, DEFAULT_PLACE_DETAILS_URL, settings::setPlaceDetailsUrl)
        ServerUrlField(Res.string.server_category_search, categorySearchUrl, DEFAULT_CATEGORY_SEARCH_URL, settings::setCategorySearchUrl)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        ListItem(
            headlineContent = { Text(stringResource(Res.string.about)) },
            leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onOpenAbout).padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Saves when the keyboard's done action is used or the field loses focus. An empty field means the default server. */
@Composable
private fun ServerUrlField(
    label: StringResource,
    current: String?,
    default: String,
    onSave: (String?) -> Unit,
) {
    var text by rememberSaveable(current) { mutableStateOf(current.orEmpty()) }
    val trimmed = text.trim()
    val valid = trimmed.isEmpty() || trimmed.startsWith("https://") || trimmed.startsWith("http://")
    val focusManager = LocalFocusManager.current

    fun save() {
        if (valid && trimmed != current.orEmpty()) onSave(trimmed.ifEmpty { null })
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(label)) },
        placeholder = { Text(default) },
        isError = !valid,
        supportingText = if (valid) null else ({ Text(stringResource(Res.string.server_invalid)) }),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(onDone = {
                save()
                focusManager.clearFocus()
            }),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .onFocusChanged { if (!it.isFocused) save() },
    )
}
