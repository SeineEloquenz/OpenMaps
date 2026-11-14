package nz.eloque.openmaps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.settings.SettingsSwitch
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.WalkingSpeed
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.mode_bus
import nz.eloque.openmaps.ui.resources.mode_ferry
import nz.eloque.openmaps.ui.resources.mode_long_distance
import nz.eloque.openmaps.ui.resources.mode_regional
import nz.eloque.openmaps.ui.resources.mode_subway_tram
import nz.eloque.openmaps.ui.resources.options_transfers
import nz.eloque.openmaps.ui.resources.options_transport
import nz.eloque.openmaps.ui.resources.options_walking
import nz.eloque.openmaps.ui.resources.options_wheelchair
import nz.eloque.openmaps.ui.resources.options_wheelchair_detail
import nz.eloque.openmaps.ui.resources.transfers_any
import nz.eloque.openmaps.ui.resources.transfers_direct
import nz.eloque.openmaps.ui.resources.transfers_up_to
import nz.eloque.openmaps.ui.resources.walking_fast
import nz.eloque.openmaps.ui.resources.walking_normal
import nz.eloque.openmaps.ui.resources.walking_slow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val TransferChoices = listOf(null, 0, 1, 2)

/** Edits how routes are planned. The last selected kind of transport cannot be switched off. */
@Composable
fun RouteOptionsEditor(
    options: RouteOptions,
    onChange: (RouteOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OptionGroup(Res.string.options_transport) {
            ModeGroup.entries.forEach { group ->
                val selected = group in options.modes
                FilterChip(
                    selected = selected,
                    onClick = {
                        val modes = if (selected) options.modes - group else options.modes + group
                        if (modes.isNotEmpty()) onChange(options.copy(modes = modes))
                    },
                    label = { Text(stringResource(group.label)) },
                )
            }
        }
        OptionGroup(Res.string.options_transfers) {
            TransferChoices.forEach { choice ->
                FilterChip(
                    selected = options.maxTransfers == choice,
                    onClick = { onChange(options.copy(maxTransfers = choice)) },
                    label = {
                        Text(
                            when (choice) {
                                null -> stringResource(Res.string.transfers_any)
                                0 -> stringResource(Res.string.transfers_direct)
                                else -> stringResource(Res.string.transfers_up_to, choice)
                            },
                        )
                    },
                )
            }
        }
        OptionGroup(Res.string.options_walking) {
            WalkingSpeed.entries.forEach { speed ->
                FilterChip(
                    selected = options.walkingSpeed == speed,
                    onClick = { onChange(options.copy(walkingSpeed = speed)) },
                    label = { Text(stringResource(speed.label)) },
                )
            }
        }
        SettingsSwitch(
            title = stringResource(Res.string.options_wheelchair),
            subtitle = stringResource(Res.string.options_wheelchair_detail),
            checked = options.wheelchair,
            onCheckedChange = { onChange(options.copy(wheelchair = it)) },
        )
    }
}

@Composable
private fun OptionGroup(
    title: StringResource,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private val ModeGroup.label: StringResource
    get() =
        when (this) {
            ModeGroup.LONG_DISTANCE_TRAIN -> Res.string.mode_long_distance
            ModeGroup.REGIONAL_TRAIN -> Res.string.mode_regional
            ModeGroup.SUBWAY_TRAM -> Res.string.mode_subway_tram
            ModeGroup.BUS -> Res.string.mode_bus
            ModeGroup.FERRY -> Res.string.mode_ferry
        }

private val WalkingSpeed.label: StringResource
    get() =
        when (this) {
            WalkingSpeed.SLOW -> Res.string.walking_slow
            WalkingSpeed.NORMAL -> Res.string.walking_normal
            WalkingSpeed.FAST -> Res.string.walking_fast
        }
