package nz.eloque.openmaps.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.OpenMap
import nz.eloque.openmaps.map.rememberMapController
import nz.eloque.openmaps.ui.home.HomeSheet
import nz.eloque.openmaps.ui.navigation.HomeKey
import nz.eloque.openmaps.ui.navigation.PlaceKey
import nz.eloque.openmaps.ui.navigation.SearchKey
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.toKey
import nz.eloque.openmaps.ui.place.PlaceSheet
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.my_location
import nz.eloque.openmaps.ui.resources.search_hint
import nz.eloque.openmaps.ui.resources.waiting_for_location
import nz.eloque.openmaps.ui.search.SearchScreen
import nz.eloque.openmaps.ui.stop.StopSheet
import nz.eloque.openmaps.ui.theme.OpenMapsTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val navConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(HomeKey::class)
                    subclass(SearchKey::class)
                    subclass(PlaceKey::class)
                    subclass(StopKey::class)
                }
            }
    }

private val SheetPeekHeight = 280.dp
private val ControlMargin = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    OpenMapsTheme {
        val backStack = rememberNavBackStack(navConfiguration, HomeKey)
        val map = rememberMapController()
        val snackbar = remember { SnackbarHostState() }
        val sheetState = rememberBottomSheetScaffoldState(snackbarHostState = snackbar)

        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = SheetPeekHeight,
            sheetContent = {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider =
                        entryProvider {
                            entry<HomeKey> { HomeSheet(map, onOpenStop = backStack::add) }
                            entry<SearchKey> {
                                SearchScreen(
                                    near = map.userLocation,
                                    onDismiss = { backStack.removeLastOrNull() },
                                    onSelect = { result ->
                                        backStack.removeLastOrNull()
                                        backStack.add(result.toKey())
                                    },
                                )
                            }
                            entry<PlaceKey> { PlaceSheet(it, map) }
                            entry<StopKey> { StopSheet(it, map) }
                        },
                )
            },
        ) {
            MapLayer(
                map = map,
                showSearchBar = backStack.lastOrNull() == HomeKey,
                onSearch = { backStack.add(SearchKey) },
                snackbar = snackbar,
            )
        }
    }
}

@Composable
private fun MapLayer(
    map: MapController,
    showSearchBar: Boolean,
    onSearch: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val mapPadding =
        PaddingValues(
            start = safe.calculateStartPadding(direction),
            top = safe.calculateTopPadding() + if (showSearchBar) SearchBarHeight + ControlMargin else 0.dp,
            end = safe.calculateEndPadding(direction),
            bottom = SheetPeekHeight + FabSize + ControlMargin,
        )

    Box(Modifier.fillMaxSize()) {
        OpenMap(map, Modifier.fillMaxSize(), contentPadding = mapPadding)

        if (showSearchBar) {
            SearchPill(onSearch, Modifier.statusBarsPadding().padding(ControlMargin))
        }

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = ControlMargin, bottom = SheetPeekHeight + ControlMargin),
            onClick = {
                if (!map.isLocationPermissionGranted) {
                    map.requestLocationPermission()
                } else {
                    scope.launch {
                        if (!map.centerOnUser()) snackbar.showSnackbar(getString(Res.string.waiting_for_location))
                    }
                }
            },
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = stringResource(Res.string.my_location))
        }
    }
}

private val SearchBarHeight = 56.dp
private val FabSize = 56.dp

@Composable
private fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text(
                stringResource(Res.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
