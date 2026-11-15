package nz.eloque.openmaps.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.RoadEvents
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.OpenMap
import nz.eloque.openmaps.map.rememberMapController
import nz.eloque.openmaps.ui.about.AboutPage
import nz.eloque.openmaps.ui.category.CategoryChips
import nz.eloque.openmaps.ui.category.CategoryChipsHeight
import nz.eloque.openmaps.ui.category.CategorySheet
import nz.eloque.openmaps.ui.category.label
import nz.eloque.openmaps.ui.components.StopsOnMap
import nz.eloque.openmaps.ui.directions.DirectionsSheet
import nz.eloque.openmaps.ui.directions.RouteSheet
import nz.eloque.openmaps.ui.directions.TripSheet
import nz.eloque.openmaps.ui.home.HomeSheet
import nz.eloque.openmaps.ui.navigation.AboutKey
import nz.eloque.openmaps.ui.navigation.CategoryKey
import nz.eloque.openmaps.ui.navigation.DirectionsField
import nz.eloque.openmaps.ui.navigation.DirectionsKey
import nz.eloque.openmaps.ui.navigation.HomeKey
import nz.eloque.openmaps.ui.navigation.PlaceKey
import nz.eloque.openmaps.ui.navigation.RoadEventKey
import nz.eloque.openmaps.ui.navigation.RouteKey
import nz.eloque.openmaps.ui.navigation.SavedKey
import nz.eloque.openmaps.ui.navigation.SearchKey
import nz.eloque.openmaps.ui.navigation.SettingsKey
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.TripKey
import nz.eloque.openmaps.ui.navigation.toEndpoint
import nz.eloque.openmaps.ui.navigation.toKey
import nz.eloque.openmaps.ui.place.PlaceSheet
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.clear
import nz.eloque.openmaps.ui.resources.dropped_pin
import nz.eloque.openmaps.ui.resources.my_location
import nz.eloque.openmaps.ui.resources.search_hint
import nz.eloque.openmaps.ui.resources.settings
import nz.eloque.openmaps.ui.resources.waiting_for_location
import nz.eloque.openmaps.ui.roads.RoadEventSheet
import nz.eloque.openmaps.ui.saved.SavedSheet
import nz.eloque.openmaps.ui.search.SearchScreen
import nz.eloque.openmaps.ui.settings.SettingsPage
import nz.eloque.openmaps.ui.sharing.SharingSheet
import nz.eloque.openmaps.ui.stop.StopSheet
import nz.eloque.openmaps.ui.theme.OpenMapsTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.random.Random

private val navConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(HomeKey::class)
                    subclass(AboutKey::class)
                    subclass(SavedKey::class)
                    subclass(SettingsKey::class)
                    subclass(SearchKey::class)
                    subclass(PlaceKey::class)
                    subclass(StopKey::class)
                    subclass(DirectionsKey::class)
                    subclass(RouteKey::class)
                    subclass(CategoryKey::class)
                    subclass(TripKey::class)
                    subclass(RoadEventKey::class)
                }
            }
    }

private val SheetPeekHeight = 280.dp

/** Shows only the drag handle and the favourites row, so the map has the screen on start. */
private val HomePeekHeight = 104.dp
private const val CATEGORY_PEEK_FRACTION = 0.45f

/** Differs per app process, so a sheet position saved by an earlier process is not restored and the sheet starts collapsed. */
private val ProcessStart = Random.nextInt()
private val ControlMargin = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(version: String) {
    val http = koinInject<HttpClient>()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory(httpClient = { http })) }.build()
    }
    OpenMapsTheme {
        val backStack = rememberNavBackStack(navConfiguration, HomeKey)
        val map = rememberMapController(loadStyle = { url -> http.get(url).bodyAsText() })
        val snackbar = remember { SnackbarHostState() }
        val sheetState =
            rememberBottomSheetScaffoldState(
                bottomSheetState = key(ProcessStart) { rememberStandardBottomSheetState() },
                snackbarHostState = snackbar,
            )
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
        DisposableEffect(map) {
            map.onPlaceClick = { place ->
                val top = backStack.lastOrNull()
                if (top is StopKey || top is PlaceKey) backStack.removeLastOrNull()
                backStack.add(place.toKey())
            }
            onDispose { map.onPlaceClick = null }
        }
        val roadEvents = koinInject<RoadEvents>()
        LaunchedEffect(map) { roadEvents.tiles.collect { map.roadEvents = it } }
        DisposableEffect(map) {
            map.onRoadEventClick = { properties ->
                val top = backStack.lastOrNull()
                if (top is StopKey || top is PlaceKey || top is RoadEventKey) backStack.removeLastOrNull()
                backStack.add(RoadEventKey(properties))
            }
            onDispose { map.onRoadEventClick = null }
        }
        LaunchedEffect(map) {
            snapshotFlow { backStack.any { it is CategoryKey } }.collect { if (!it) map.results = emptyList() }
        }

        val top = backStack.lastOrNull()
        val sheetTop = backStack.lastOrNull { !it.isPage() }
        LaunchedEffect(sheetTop) {
            if (sheetState.bottomSheetState.currentValue == SheetValue.Expanded) sheetState.bottomSheetState.partialExpand()
        }
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val peekHeight =
            when {
                top == HomeKey -> HomePeekHeight
                top is CategoryKey -> windowHeight * CATEGORY_PEEK_FRACTION
                else -> SheetPeekHeight
            } +
                with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

        Box(Modifier.fillMaxSize()) {
            BottomSheetScaffold(
                scaffoldState = sheetState,
                sheetPeekHeight = peekHeight,
                sheetContent = {
                    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
                    NavDisplay(
                        // Without full height, content shorter than the peek makes the sheet count as expanded,
                        // so it opens fully by itself once results load.
                        modifier = Modifier.fillMaxHeight().belowStatusBar(statusBar) { sheetState.bottomSheetState.requireOffset() },
                        backStack = backStack.filterNot { it.isPage() },
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
                                        onOpenSaved = { backStack.add(SavedKey) },
                                        onOpenTrip = { tripId, stopName -> backStack.add(TripKey(tripId, stopName)) },
                                    )
                                }
                                entry<SavedKey> {
                                    SavedSheet(
                                        onOpenPlace = { backStack.add(it.toKey()) },
                                        onOpenRoute = { backStack.add(DirectionsKey(it.from?.toEndpoint(), it.to?.toEndpoint())) },
                                    )
                                }
                                entry<SearchKey> { search ->
                                    SearchScreen(
                                        near = map.userLocation,
                                        stopsFirst = search.field != null,
                                        onDismiss = { backStack.removeLastOrNull() },
                                        onCategory =
                                            if (search.field == null && search.saveAs == null) {
                                                { category ->
                                                    backStack.removeLastOrNull()
                                                    if (backStack.lastOrNull() is CategoryKey) backStack.removeLastOrNull()
                                                    backStack.add(CategoryKey(category))
                                                }
                                            } else {
                                                null
                                            },
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
                                    PlaceSheet(
                                        place,
                                        map,
                                        onDirections = { backStack.add(DirectionsKey(from = null, to = place.toEndpoint())) },
                                        onOpenStop = backStack::add,
                                        onOpenTrip = { tripId, stopName -> backStack.add(TripKey(tripId, stopName)) },
                                    )
                                }
                                entry<StopKey> { stop ->
                                    StopSheet(
                                        stop = stop,
                                        map = map,
                                        onDirections = { backStack.add(DirectionsKey(from = null, to = stop.toEndpoint())) },
                                        onOpenTrip = { backStack.add(TripKey(it, stop.name)) },
                                    )
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
                                entry<CategoryKey> {
                                    if (it.category == PlaceCategory.SHARED_VEHICLES) {
                                        SharingSheet(map)
                                        return@entry
                                    }
                                    CategorySheet(
                                        it,
                                        map,
                                        onOpenPlace = { place -> backStack.add(place.toKey()) },
                                        onDirections = { place -> backStack.add(DirectionsKey(from = null, to = place.toEndpoint())) },
                                    )
                                }
                                entry<TripKey> { TripSheet(it, map) }
                                entry<RoadEventKey> { RoadEventSheet(it) }
                            },
                    )
                },
            ) {
                MapLayer(
                    map = map,
                    peekHeight = peekHeight,
                    showSearchBar = top == HomeKey || top is CategoryKey,
                    category = (top as? CategoryKey)?.category,
                    onCategory = { category ->
                        if (top is CategoryKey) backStack.removeLastOrNull()
                        if (category != null) backStack.add(CategoryKey(category))
                    },
                    onSearch = { backStack.add(SearchKey()) },
                    onSettings = { backStack.add(SettingsKey) },
                    snackbar = snackbar,
                )
            }
            AnimatedContent(top?.takeIf { it.isPage() }, transitionSpec = { fadeIn() togetherWith fadeOut() }) { page ->
                when (page) {
                    SettingsKey -> SettingsPage(onBack = { backStack.removeLastOrNull() }, onOpenAbout = { backStack.add(AboutKey) })
                    AboutKey -> AboutPage(version, onBack = { backStack.removeLastOrNull() })
                    else -> {}
                }
            }
        }
    }
}

/** Pushes the content down by as much of [statusBar] as the sheet's top edge, at [sheetTop] pixels, has slid under. */
private fun Modifier.belowStatusBar(
    statusBar: Int,
    sheetTop: () -> Float,
) = layout { measurable, constraints ->
    val inset = (statusBar - (runCatching(sheetTop).getOrNull()?.toInt() ?: statusBar)).coerceIn(0, statusBar)
    val placeable = measurable.measure(constraints.offset(vertical = -inset))
    layout(placeable.width, placeable.height + inset) { placeable.place(0, inset) }
}

/** Keys shown as full-screen pages over the map instead of in the sheet. */
private fun NavKey.isPage() = this == SettingsKey || this == AboutKey

@Composable
private fun MapLayer(
    map: MapController,
    peekHeight: Dp,
    showSearchBar: Boolean,
    category: PlaceCategory?,
    onCategory: (PlaceCategory?) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val mapPadding =
        PaddingValues(
            start = safe.calculateStartPadding(direction),
            top = safe.calculateTopPadding() + if (showSearchBar) SearchBarHeight + CategoryChipsHeight + ControlMargin * 2 else 0.dp,
            end = safe.calculateEndPadding(direction),
            bottom = peekHeight + FabSize + ControlMargin,
        )

    Box(Modifier.fillMaxSize()) {
        OpenMap(map, Modifier.fillMaxSize(), contentPadding = mapPadding)

        if (showSearchBar) {
            Column(Modifier.statusBarsPadding().padding(top = ControlMargin)) {
                SearchPill(
                    query = category?.let { stringResource(it.label) },
                    onClick = onSearch,
                    onClear = { onCategory(null) },
                    onSettings = onSettings,
                    modifier = Modifier.padding(horizontal = ControlMargin),
                )
                CategoryChips(
                    selected = category,
                    onSelect = onCategory,
                    contentPadding = PaddingValues(horizontal = ControlMargin),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = ControlMargin, bottom = peekHeight + ControlMargin),
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
    query: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
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
                query ?: stringResource(Res.string.search_hint),
                color = if (query != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (query != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear))
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    }
}
