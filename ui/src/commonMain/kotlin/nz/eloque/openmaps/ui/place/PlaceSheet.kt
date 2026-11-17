package nz.eloque.openmaps.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import nz.eloque.openmaps.core.model.Accessibility
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.OpeningHours
import nz.eloque.openmaps.core.model.ParkedCar
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceFeature
import nz.eloque.openmaps.core.model.PlaceMedia
import nz.eloque.openmaps.core.model.PricedStation
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.formatted
import nz.eloque.openmaps.core.model.plusCode
import nz.eloque.openmaps.core.platform.Sharer
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.category.label
import nz.eloque.openmaps.ui.components.DirectionsButton
import nz.eloque.openmaps.ui.components.FocusOnMap
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.NearbyStopCard
import nz.eloque.openmaps.ui.components.SaveButton
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.components.formatPrice
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.navigation.PlaceKey
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.parsedId
import nz.eloque.openmaps.ui.navigation.toSearchResult
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.address
import nz.eloque.openmaps.ui.resources.call
import nz.eloque.openmaps.ui.resources.coordinates
import nz.eloque.openmaps.ui.resources.cuisine
import nz.eloque.openmaps.ui.resources.email
import nz.eloque.openmaps.ui.resources.fact_cash_deposit
import nz.eloque.openmaps.ui.resources.feature_air_conditioning
import nz.eloque.openmaps.ui.resources.feature_cards
import nz.eloque.openmaps.ui.resources.feature_delivery
import nz.eloque.openmaps.ui.resources.feature_dogs
import nz.eloque.openmaps.ui.resources.feature_drive_through
import nz.eloque.openmaps.ui.resources.feature_emergency
import nz.eloque.openmaps.ui.resources.feature_gluten_free
import nz.eloque.openmaps.ui.resources.feature_halal
import nz.eloque.openmaps.ui.resources.feature_kosher
import nz.eloque.openmaps.ui.resources.feature_organic
import nz.eloque.openmaps.ui.resources.feature_outdoor_seating
import nz.eloque.openmaps.ui.resources.feature_reservation_required
import nz.eloque.openmaps.ui.resources.feature_reservations
import nz.eloque.openmaps.ui.resources.feature_takeaway
import nz.eloque.openmaps.ui.resources.feature_toilets
import nz.eloque.openmaps.ui.resources.feature_vegan
import nz.eloque.openmaps.ui.resources.feature_vegetarian
import nz.eloque.openmaps.ui.resources.feature_wheelchair_toilet
import nz.eloque.openmaps.ui.resources.feature_wifi
import nz.eloque.openmaps.ui.resources.filter_baby_changing
import nz.eloque.openmaps.ui.resources.filter_bottle_refill
import nz.eloque.openmaps.ui.resources.fuel_prices
import nz.eloque.openmaps.ui.resources.measure_distance
import nz.eloque.openmaps.ui.resources.nearby_departures
import nz.eloque.openmaps.ui.resources.operator
import nz.eloque.openmaps.ui.resources.phone
import nz.eloque.openmaps.ui.resources.photos_commons
import nz.eloque.openmaps.ui.resources.photos_nearby
import nz.eloque.openmaps.ui.resources.prices_from
import nz.eloque.openmaps.ui.resources.prices_updated
import nz.eloque.openmaps.ui.resources.read_on_wikipedia
import nz.eloque.openmaps.ui.resources.report
import nz.eloque.openmaps.ui.resources.save_parking
import nz.eloque.openmaps.ui.resources.share
import nz.eloque.openmaps.ui.resources.website
import nz.eloque.openmaps.ui.resources.wheelchair
import nz.eloque.openmaps.ui.resources.wheelchair_limited
import nz.eloque.openmaps.ui.resources.wheelchair_no
import nz.eloque.openmaps.ui.resources.wheelchair_yes
import nz.eloque.openmaps.ui.weather.WarningCard
import nz.eloque.openmaps.ui.weather.rememberWarnings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun PlaceSheet(
    place: PlaceKey,
    map: MapController,
    onDirections: () -> Unit,
    onOpenStop: (StopKey) -> Unit,
    onOpenTrip: (tripId: String, stopName: String) -> Unit,
    onMeasure: () -> Unit,
) {
    val viewModel = koinViewModel<PlaceViewModel> { parametersOf(place.parsedId, place.location, !place.pinned, place.category == "fuel") }
    val details by viewModel.details.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val now by rememberNow()
    val loaded = (details as? LoadState.Loaded)?.value
    FocusOnMap(map, place.location, zoomIn = !place.pinned)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(place.name, style = MaterialTheme.typography.headlineSmall)
            val stars = loaded?.stars?.takeIf { it in 1..7 }?.let { "★".repeat(it) }
            val subtitle = listOfNotNull(place.category?.toCategoryLabel(), stars, place.subtitle).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusLine(place, loaded, map)
        }
        ActionRow(place, loaded, shareUrl = viewModel.pageUrl ?: map.linkTo(place.location), onDirections, onMeasure)
        rememberWarnings(place.location).forEach { WarningCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        media?.let { MediaSection(it) }
        loaded?.description?.let {
            Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            prices?.let { FuelPriceSection(it, now) }
            LoadStateContent(details, onRetry = viewModel::load) { it?.let { DetailRows(it, place.name) } }
            CoordinatesRow(place.location)
            if (!place.pinned) ReviewsSection(place.name, place.location)
            val stops = (nearby as? LoadState.Loaded)?.value.orEmpty()
            if (stops.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(Res.string.nearby_departures), style = MaterialTheme.typography.titleMedium)
                stops.forEach { stop ->
                    NearbyStopCard(stop, now, onOpenTrip = onOpenTrip, onClick = {
                        stop.id?.let { onOpenStop(StopKey(it, stop.name, stop.location.latitude, stop.location.longitude)) }
                    })
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    place: PlaceKey,
    details: PlaceDetails?,
    map: MapController,
) {
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()
    val hours = details?.openingHours?.let(OpeningHours::parse)
    val distance = map.userLocation?.let { formatDistance(it.distanceTo(place.location), unit) }
    val accessible = details?.wheelchair == Accessibility.YES
    if (hours == null && distance == null && !accessible) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        hours?.let { OpenStatusText(it) }
        distance?.let {
            Text(
                if (hours != null) " · $it" else it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (accessible) {
            Icon(
                Icons.AutoMirrored.Filled.Accessible,
                contentDescription = stringResource(Res.string.wheelchair_yes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(
    place: PlaceKey,
    details: PlaceDetails?,
    shareUrl: String,
    onDirections: () -> Unit,
    onMeasure: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val sharer = koinInject<Sharer>()
    val settings = koinInject<SettingsStore>()
    var reporting by remember { mutableStateOf(false) }
    if (reporting) ReportDialog(place.location, place.name.takeUnless { place.pinned }, onDismiss = { reporting = false })
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { DirectionsButton(onDirections) }
        item { SaveButton(place.toSearchResult()) }
        details?.phone?.let { phone ->
            item { ActionButton(Icons.Default.Call, Res.string.call) { uriHandler.openUri(phone.telUri()) } }
        }
        details?.website?.let { url ->
            item { ActionButton(Icons.Default.Language, Res.string.website) { uriHandler.openUri(url) } }
        }
        item { ActionButton(Icons.Default.Share, Res.string.share) { sharer.share("${place.name}\n$shareUrl") } }
        item { ActionButton(Icons.Default.Straighten, Res.string.measure_distance, onMeasure) }
        item {
            ActionButton(Icons.Default.LocalParking, Res.string.save_parking) {
                settings.setParkedCar(ParkedCar(place.location, Clock.System.now()))
            }
        }
        item { ActionButton(Icons.Outlined.Flag, Res.string.report) { reporting = true } }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: StringResource,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MediaSection(media: PlaceMedia) {
    val uriHandler = LocalUriHandler.current
    if (media.photos.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items(media.photos, key = { it.pageUrl }) { photo ->
                Box(
                    Modifier
                        .size(width = 240.dp, height = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { uriHandler.openUri(photo.pageUrl) },
                ) {
                    AsyncImage(
                        model = photo.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 240.dp, height = 180.dp),
                    )
                    photo.credit?.let { credit ->
                        Text(
                            credit,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Text(
            stringResource(if (media.photosNearby) Res.string.photos_nearby else Res.string.photos_commons),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    media.article?.let { article ->
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(article.extract, maxLines = 5, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { uriHandler.openUri(article.url) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(Res.string.read_on_wikipedia))
            }
        }
    }
}

@Composable
private fun DetailRows(
    details: PlaceDetails,
    placeName: String,
) {
    val uriHandler = LocalUriHandler.current
    details.address?.let { DetailRow(Icons.Outlined.Place, stringResource(Res.string.address), it) }
    details.openingHours?.let { OpeningHoursRow(it) }
    details.website?.let { url ->
        DetailRow(Icons.Default.Language, stringResource(Res.string.website), url) { uriHandler.openUri(url) }
    }
    details.phone?.let { phone ->
        DetailRow(Icons.Default.Phone, stringResource(Res.string.phone), phone) { uriHandler.openUri(phone.telUri()) }
    }
    details.email?.let { email ->
        DetailRow(Icons.Default.Email, stringResource(Res.string.email), email) { uriHandler.openUri("mailto:$email") }
    }
    if (details.cuisine.isNotEmpty()) {
        DetailRow(Icons.Default.Restaurant, stringResource(Res.string.cuisine), details.cuisine.joinToString(", ") { it.toCategoryLabel() })
    }
    details.operator?.takeIf { it != placeName }?.let { DetailRow(Icons.Default.Business, stringResource(Res.string.operator), it) }
    details.wheelchair?.let { value ->
        val label =
            when (value) {
                Accessibility.YES -> Res.string.wheelchair_yes
                Accessibility.LIMITED -> Res.string.wheelchair_limited
                Accessibility.NO -> Res.string.wheelchair_no
            }
        DetailRow(Icons.AutoMirrored.Filled.Accessible, stringResource(Res.string.wheelchair), stringResource(label))
    }
    if (details.features.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            PlaceFeature.entries.filter { it in details.features }.forEach { FeatureLabel(it) }
        }
    }
}

@Composable
private fun FeatureLabel(feature: PlaceFeature) {
    val (icon, label) = feature.display()
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun PlaceFeature.display(): Pair<ImageVector, StringResource> =
    when (this) {
        PlaceFeature.EMERGENCY -> Icons.Default.LocalHospital to Res.string.feature_emergency
        PlaceFeature.TAKEAWAY -> Icons.Default.TakeoutDining to Res.string.feature_takeaway
        PlaceFeature.DELIVERY -> Icons.Default.DeliveryDining to Res.string.feature_delivery
        PlaceFeature.OUTDOOR_SEATING -> Icons.Default.Deck to Res.string.feature_outdoor_seating
        PlaceFeature.WIFI -> Icons.Default.Wifi to Res.string.feature_wifi
        PlaceFeature.VEGETARIAN -> Icons.Default.Eco to Res.string.feature_vegetarian
        PlaceFeature.VEGAN -> Icons.Default.Grass to Res.string.feature_vegan
        PlaceFeature.GLUTEN_FREE -> Icons.Default.Grain to Res.string.feature_gluten_free
        PlaceFeature.CARDS -> Icons.Default.CreditCard to Res.string.feature_cards
        PlaceFeature.TOILETS -> Icons.Default.Wc to Res.string.feature_toilets
        PlaceFeature.CASH_DEPOSIT -> Icons.Default.LocalAtm to Res.string.fact_cash_deposit
        PlaceFeature.CHANGING_TABLE -> Icons.Default.BabyChangingStation to Res.string.filter_baby_changing
        PlaceFeature.BOTTLE_REFILL -> Icons.Default.WaterDrop to Res.string.filter_bottle_refill
        PlaceFeature.HALAL -> Icons.Default.RestaurantMenu to Res.string.feature_halal
        PlaceFeature.KOSHER -> Icons.Default.RestaurantMenu to Res.string.feature_kosher
        PlaceFeature.ORGANIC -> Icons.Default.Spa to Res.string.feature_organic
        PlaceFeature.RESERVATIONS -> Icons.Default.EventSeat to Res.string.feature_reservations
        PlaceFeature.RESERVATION_REQUIRED -> Icons.Default.EventSeat to Res.string.feature_reservation_required
        PlaceFeature.DRIVE_THROUGH -> Icons.Default.DirectionsCar to Res.string.feature_drive_through
        PlaceFeature.AIR_CONDITIONING -> Icons.Default.AcUnit to Res.string.feature_air_conditioning
        PlaceFeature.DOGS -> Icons.Default.Pets to Res.string.feature_dogs
        PlaceFeature.WHEELCHAIR_TOILET -> Icons.AutoMirrored.Filled.Accessible to Res.string.feature_wheelchair_toilet
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

/** Copies the coordinates and Plus Code of [location] when tapped. */
@Suppress("DEPRECATION")
@Composable
private fun CoordinatesRow(location: LatLng) {
    val clipboard = LocalClipboardManager.current
    val text = "${location.formatted()} · ${location.plusCode()}"
    DetailRow(Icons.Default.MyLocation, stringResource(Res.string.coordinates), text) { clipboard.setText(AnnotatedString(text)) }
}

internal fun String.telUri() = "tel:${filter { c -> c.isDigit() || c == '+' }}"

/** Turns a place type or cuisine code such as `fast_food` into a label. */
fun String.toCategoryLabel(): String = replace('_', ' ').let { if (it == "atm") "ATM" else it.replaceFirstChar { c -> c.uppercase() } }

@Composable
private fun FuelPriceSection(
    station: PricedStation,
    now: Instant,
) {
    val uriHandler = LocalUriHandler.current
    Text(stringResource(Res.string.fuel_prices), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    station.prices.sortedBy { it.fuel }.forEach { price ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(price.fuel.label, modifier = Modifier.weight(1f))
            Text(formatPrice(price.price, price.currency), fontWeight = FontWeight.Bold)
        }
    }
    val updated = station.prices.mapNotNull { it.updated }.maxOrNull()
    Text(
        listOfNotNull(
            updated?.let { stringResource(Res.string.prices_updated, formatDuration(now - it)) },
            stringResource(Res.string.prices_from, station.source.source),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { uriHandler.openUri(station.source.url) }.padding(vertical = 4.dp),
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}
