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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.OpenMap
import nz.eloque.openmaps.map.rememberMapController
import nz.eloque.openmaps.ui.about.AboutSheet
import nz.eloque.openmaps.ui.components.StopsOnMap
import nz.eloque.openmaps.ui.directions.DirectionsSheet
import nz.eloque.openmaps.ui.directions.RouteSheet
import nz.eloque.openmaps.ui.home.HomeSheet
import nz.eloque.openmaps.ui.navigation.AboutKey
import nz.eloque.openmaps.ui.navigation.DirectionsField
import nz.eloque.openmaps.ui.navigation.DirectionsKey
import nz.eloque.openmaps.ui.navigation.HomeKey
import nz.eloque.openmaps.ui.navigation.PlaceKey
import nz.eloque.openmaps.ui.navigation.RouteKey
import nz.eloque.openmaps.ui.navigation.SearchKey
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.toEndpoint
import nz.eloque.openmaps.ui.navigation.toKey
import nz.eloque.openmaps.ui.place.PlaceSheet
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.about
import nz.eloque.openmaps.ui.resources.dropped_pin
import nz.eloque.openmaps.ui.resources.my_location
import nz.eloque.openmaps.ui.resources.search_hint
import nz.eloque.openmaps.ui.resources.waiting_for_location
import nz.eloque.openmaps.ui.search.SearchScreen
import nz.eloque.openmaps.ui.stop.StopSheet
import nz.eloque.openmaps.ui.theme.OpenMapsTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val navConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(HomeKey::class)
                    subclass(AboutKey::class)
                    subclass(SearchKey::class)
                    subclass(PlaceKey::class)
                    subclass(StopKey::class)
                    subclass(DirectionsKey::class)
                    subclass(RouteKey::class)
                }
            }
    }

private val SheetPeekHeight = 280.dp
private val ControlMargin = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(version: String) {
    OpenMapsTheme {
        val backStack = rememberNavBackStack(navConfiguration, HomeKey)
        val map = rememberMapController()
        val snackbar = remember { SnackbarHostState() }
        val sheetState = rememberBottomSheetScaffoldState(snackbarHostState = snackbar)
        val places = koinInject<PlacesStore>()
        val scope = rememberCoroutineScope()
        val geocoder = koinInject<Geocoder>()
        val pinName = stringResource(Res.string.dropped_pin)
        DisposableEffect(map) {
            map.onLongPress = { location ->
                val pin = PlaceKey(pinName, location.latitude, location.longitude, null, null, null, pinned = true)
                if ((backStack.lastOrNull() as? PlaceKey)?.pinned == true) backStack.removeLastOrNull()
                backStack.add(pin)
                scope.launch {
                    val address = runCatching { geocoder.reverse(location) }.getOrNull() ?: return@launch
                    val index = backStack.indexOf(pin)
                    if (index >= 0) backStack[index] = pin.copy(name = address.name, subtitle = address.area)
                }
            }
            onDispose { map.onLongPress = null }
        }
        StopsOnMap(map) { stop ->
            val top = backStack.lastOrNull()
            if (top is StopKey || (top as? PlaceKey)?.pinned == true) backStack.removeLastOrNull()
            backStack.add(stop.toKey())
        }

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
                            entry<HomeKey> {
                                HomeSheet(
                                    map = map,
                                    onOpenStop = backStack::add,
                                    onDirectionsTo = { backStack.add(DirectionsKey(from = null, to = it.toEndpoint())) },
                                    onSetFavourite = { backStack.add(SearchKey(saveAs = it)) },
                                )
                            }
                            entry<AboutKey> { AboutSheet(version) }
                            entry<SearchKey> { search ->
                                SearchScreen(
                                    near = map.userLocation,
                                    stopsFirst = search.field != null,
                                    onDismiss = { backStack.removeLastOrNull() },
                                    onSelect = { result ->
                                        backStack.removeLastOrNull()
                                        search.saveAs?.let { label ->
                                            scope.launch { places.saveFavourite(result, label) }
                                            return@SearchScreen
                                        }
                                        val field = search.field
                                        val index = backStack.indexOfLast { it is DirectionsKey }
                                        if (field == null || index < 0) {
                                            backStack.add(result.toKey())
                                        } else {
                                            val directions = backStack[index] as DirectionsKey
                                            backStack[index] =
                                                when (field) {
                                                    DirectionsField.FROM -> directions.copy(from = result.toEndpoint())
                                                    DirectionsField.TO -> directions.copy(to = result.toEndpoint())
                                                }
                                        }
                                    },
                                )
                            }
                            entry<PlaceKey> { place ->
                                PlaceSheet(place, map, onDirections = {
                                    backStack.add(DirectionsKey(from = null, to = place.toEndpoint()))
                                })
                            }
                            entry<StopKey> { stop ->
                                StopSheet(stop, map, onDirections = { backStack.add(DirectionsKey(from = null, to = stop.toEndpoint())) })
                            }
                            entry<DirectionsKey> { directions ->
                                DirectionsSheet(
                                    directions = directions,
                                    map = map,
                                    onEditField = { backStack.add(SearchKey(it)) },
                                    onSwap = {
                                        val index = backStack.indexOf(directions)
                                        if (index >= 0) backStack[index] = DirectionsKey(from = directions.to, to = directions.from)
                                    },
                                    onOpenRoute = { backStack.add(RouteKey(it.id, directions.to?.name)) },
                                )
                            }
                            entry<RouteKey> { RouteSheet(it, map, snackbar) }
                        },
                )
            },
        ) {
            MapLayer(
                map = map,
                showSearchBar = backStack.lastOrNull() == HomeKey,
                onSearch = { backStack.add(SearchKey()) },
                onAbout = { backStack.add(AboutKey) },
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
    onAbout: () -> Unit,
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
            SearchPill(onSearch, onAbout, Modifier.statusBarsPadding().padding(ControlMargin))
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
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text(
                stringResource(Res.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            IconButton(onClick = onAbout) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.about))
            }
        }
    }
}
