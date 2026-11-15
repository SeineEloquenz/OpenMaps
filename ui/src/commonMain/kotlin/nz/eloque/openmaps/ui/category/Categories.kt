package nz.eloque.openmaps.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.category_atms
import nz.eloque.openmaps.ui.resources.category_attractions
import nz.eloque.openmaps.ui.resources.category_bakeries
import nz.eloque.openmaps.ui.resources.category_bars
import nz.eloque.openmaps.ui.resources.category_cafes
import nz.eloque.openmaps.ui.resources.category_charging
import nz.eloque.openmaps.ui.resources.category_gas_stations
import nz.eloque.openmaps.ui.resources.category_hotels
import nz.eloque.openmaps.ui.resources.category_pharmacies
import nz.eloque.openmaps.ui.resources.category_restaurants
import nz.eloque.openmaps.ui.resources.category_supermarkets
import nz.eloque.openmaps.ui.resources.category_toilets
import nz.eloque.openmaps.ui.resources.clear
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val PlaceCategory.label: StringResource
    get() =
        when (this) {
            PlaceCategory.RESTAURANTS -> Res.string.category_restaurants
            PlaceCategory.CAFES -> Res.string.category_cafes
            PlaceCategory.SUPERMARKETS -> Res.string.category_supermarkets
            PlaceCategory.GAS_STATIONS -> Res.string.category_gas_stations
            PlaceCategory.CHARGING -> Res.string.category_charging
            PlaceCategory.BARS -> Res.string.category_bars
            PlaceCategory.ATTRACTIONS -> Res.string.category_attractions
            PlaceCategory.HOTELS -> Res.string.category_hotels
            PlaceCategory.PHARMACIES -> Res.string.category_pharmacies
            PlaceCategory.BAKERIES -> Res.string.category_bakeries
            PlaceCategory.ATMS -> Res.string.category_atms
            PlaceCategory.TOILETS -> Res.string.category_toilets
        }

val PlaceCategory.icon: ImageVector
    get() =
        when (this) {
            PlaceCategory.RESTAURANTS -> Icons.Default.Restaurant
            PlaceCategory.CAFES -> Icons.Default.LocalCafe
            PlaceCategory.SUPERMARKETS -> Icons.Default.LocalGroceryStore
            PlaceCategory.GAS_STATIONS -> Icons.Default.LocalGasStation
            PlaceCategory.CHARGING -> Icons.Default.EvStation
            PlaceCategory.BARS -> Icons.Default.LocalBar
            PlaceCategory.ATTRACTIONS -> Icons.Default.PhotoCamera
            PlaceCategory.HOTELS -> Icons.Default.Hotel
            PlaceCategory.PHARMACIES -> Icons.Default.LocalPharmacy
            PlaceCategory.BAKERIES -> Icons.Default.BakeryDining
            PlaceCategory.ATMS -> Icons.Default.LocalAtm
            PlaceCategory.TOILETS -> Icons.Default.Wc
        }

/** Words, in English and German, that ask for a category rather than a place by name. */
private val PlaceCategory.keywords: List<String>
    get() =
        when (this) {
            PlaceCategory.RESTAURANTS -> listOf("restaurant", "restaurants", "food", "fast food", "essen", "imbiss")
            PlaceCategory.CAFES -> listOf("cafe", "cafes", "coffee", "kaffee")
            PlaceCategory.SUPERMARKETS -> listOf("supermarket", "supermarkets", "grocery", "groceries", "supermarkt", "lebensmittel")
            PlaceCategory.GAS_STATIONS -> listOf("gas station", "gas stations", "petrol", "fuel", "tankstelle")
            PlaceCategory.CHARGING -> listOf("charging", "charging station", "ev charger", "charger", "ladestation", "ladesaule", "laden")
            PlaceCategory.BARS -> listOf("bar", "bars", "pub", "pubs", "kneipe", "biergarten")
            PlaceCategory.ATTRACTIONS -> listOf("attraction", "attractions", "sights", "sightseeing", "museum", "sehenswurdigkeit")
            PlaceCategory.HOTELS -> listOf("hotel", "hotels", "hostel", "motel", "unterkunft")
            PlaceCategory.PHARMACIES -> listOf("pharmacy", "pharmacies", "chemist", "apotheke")
            PlaceCategory.BAKERIES -> listOf("bakery", "bakeries", "backerei", "baecker", "backer")
            PlaceCategory.ATMS -> listOf("atm", "atms", "cash", "geldautomat", "bankomat")
            PlaceCategory.TOILETS -> listOf("toilet", "toilets", "wc", "restroom", "toilette")
        }

/** Categories whose keywords start with [query], so typing `caf` or `apotheke` offers a nearby search. */
fun categoriesMatching(query: String): List<PlaceCategory> {
    val typed = query.normalized()
    if (typed.isEmpty()) return emptyList()
    return PlaceCategory.entries.filter { category ->
        category.keywords.any { it.startsWith(typed) && typed.length >= minOf(MIN_TYPED, it.length) }
    }
}

private fun String.normalized(): String =
    trim()
        .lowercase()
        .replace("ß", "ss")
        .map { DiacriticFree[it] ?: it }
        .joinToString("")

private val DiacriticFree = mapOf('ä' to 'a', 'ö' to 'o', 'ü' to 'u', 'é' to 'e', 'è' to 'e', 'à' to 'a')
private const val MIN_TYPED = 3

/** Height of [CategoryChips], for keeping map controls clear of it. */
val CategoryChipsHeight: Dp = 40.dp

/** One row of category shortcuts floating over the map. Tapping the [selected] one again clears it. */
@Composable
fun CategoryChips(
    selected: PlaceCategory?,
    onSelect: (PlaceCategory?) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        selected?.let { listState.animateScrollToItem(it.ordinal) }
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PlaceCategory.entries) { category ->
            val isSelected = category == selected
            ElevatedFilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else category) },
                label = { Text(stringResource(category.label)) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon =
                    if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.clear),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                colors =
                    FilterChipDefaults.elevatedFilterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        }
    }
}
