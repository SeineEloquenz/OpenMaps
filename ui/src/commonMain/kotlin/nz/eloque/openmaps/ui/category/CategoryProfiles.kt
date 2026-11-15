package nz.eloque.openmaps.ui.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.Accessibility
import nz.eloque.openmaps.core.model.Connector
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.OpenStatus
import nz.eloque.openmaps.core.model.OpeningHours
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceFeature
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.ui.place.toCategoryLabel
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cuisine
import nz.eloque.openmaps.ui.resources.fact_cash_deposit
import nz.eloque.openmaps.ui.resources.fact_free
import nz.eloque.openmaps.ui.resources.fact_free_entry
import nz.eloque.openmaps.ui.resources.fact_paid
import nz.eloque.openmaps.ui.resources.filter_any
import nz.eloque.openmaps.ui.resources.filter_any_cuisine
import nz.eloque.openmaps.ui.resources.filter_baby_changing
import nz.eloque.openmaps.ui.resources.filter_bank
import nz.eloque.openmaps.ui.resources.filter_chain
import nz.eloque.openmaps.ui.resources.filter_connector
import nz.eloque.openmaps.ui.resources.filter_fast_charging
import nz.eloque.openmaps.ui.resources.filter_free
import nz.eloque.openmaps.ui.resources.filter_free_entry
import nz.eloque.openmaps.ui.resources.filter_fuel
import nz.eloque.openmaps.ui.resources.filter_open_now
import nz.eloque.openmaps.ui.resources.filter_outdoor_seating
import nz.eloque.openmaps.ui.resources.filter_stars
import nz.eloque.openmaps.ui.resources.filter_wheelchair
import nz.eloque.openmaps.ui.resources.filter_wifi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** A chip that narrows the results. Places without the needed information are hidden while it is on. */
internal sealed interface ResultFilter {
    val id: String
    val label: StringResource

    /** Switched on or off, with [labelArgs] filled into [label]. */
    class Toggle(
        override val id: String,
        override val label: StringResource,
        val icon: ImageVector? = null,
        val labelArgs: List<Any> = emptyList(),
        val matches: (PlaceDetails, Instant) -> Boolean,
    ) : ResultFilter

    /** One value picked from a menu of the values among the results. */
    class Choice(
        override val id: String,
        override val label: StringResource,
        val anyLabel: StringResource = Res.string.filter_any,
        val values: (PlaceDetails) -> List<String>,
        val display: (String) -> String = { it },
    ) : ResultFilter
}

/** What results of one category show and how they can be narrowed. */
internal class CategoryProfile(
    /** What sort of place it is, such as its cuisine, chain or bank. */
    val kind: (SearchResult.Place, PlaceDetails?) -> String?,
    val facts: (PlaceDetails) -> List<StringResource> = { emptyList() },
    val icons: (PlaceDetails) -> List<ImageVector> = { emptyList() },
    val filters: List<ResultFilter>,
    val showsDescription: Boolean = false,
)

/** Values of the active filters, by filter id. */
data class CategoryFilters(
    val on: Set<String> = emptySet(),
    val choices: Map<String, String> = emptyMap(),
)

internal fun CategoryProfile.allows(
    filters: CategoryFilters,
    details: PlaceDetails?,
    now: Instant,
): Boolean =
    this.filters.all { filter ->
        when (filter) {
            is ResultFilter.Toggle -> filter.id !in filters.on || (details != null && filter.matches(details, now))
            is ResultFilter.Choice ->
                filters.choices[filter.id]?.let { chosen -> details != null && chosen in filter.values(details) }
                    ?: true
        }
    }

internal val PlaceCategory.profile: CategoryProfile
    get() =
        when (this) {
            PlaceCategory.RESTAURANTS ->
                CategoryProfile(
                    kind = ::cuisineOrType,
                    icons = { icons(it, wheelchair = true, outdoor = true, takeaway = true) },
                    filters =
                        listOf(
                            OpenNow,
                            ResultFilter.Choice("cuisine", Res.string.cuisine, Res.string.filter_any_cuisine, {
                                it.cuisine
                            }, { it.toCategoryLabel() }),
                            Wheelchair,
                        ),
                )
            PlaceCategory.CAFES ->
                CategoryProfile(
                    kind = ::cuisineOrType,
                    icons = { icons(it, wheelchair = true, outdoor = true, wifi = true) },
                    filters = listOf(OpenNow, OutdoorSeating, WiFi),
                )
            PlaceCategory.BARS ->
                CategoryProfile(
                    kind = { place, _ -> place.category?.toCategoryLabel() },
                    icons = { icons(it, wheelchair = true, outdoor = true) },
                    filters = listOf(OpenNow, OutdoorSeating),
                )
            PlaceCategory.BAKERIES, PlaceCategory.SUPERMARKETS ->
                CategoryProfile(
                    kind = { place, details -> details?.brand ?: place.category?.toCategoryLabel() },
                    icons = { icons(it, wheelchair = true) },
                    filters = listOfNotNull(OpenNow, Chain.takeIf { this == PlaceCategory.SUPERMARKETS }),
                )
            PlaceCategory.PHARMACIES ->
                CategoryProfile(
                    kind = { place, details -> details?.brand ?: place.category?.toCategoryLabel() },
                    icons = { icons(it, wheelchair = true) },
                    filters = listOf(OpenNow),
                )
            PlaceCategory.ATMS ->
                CategoryProfile(
                    kind = { place, details -> details?.bank ?: place.category?.toCategoryLabel() },
                    facts = { details ->
                        listOfNotNull(Res.string.fact_cash_deposit.takeIf { PlaceFeature.CASH_DEPOSIT in details.features })
                    },
                    filters = listOf(ResultFilter.Choice("bank", Res.string.filter_bank, values = { listOfNotNull(it.bank) })),
                )
            PlaceCategory.TOILETS ->
                CategoryProfile(
                    kind = { place, _ -> place.category?.toCategoryLabel() },
                    facts = { details ->
                        when (details.free) {
                            true -> listOf(Res.string.fact_free)
                            false -> listOf(Res.string.fact_paid)
                            null -> emptyList()
                        }
                    },
                    icons = { icons(it, wheelchair = true, changingTable = true) },
                    filters =
                        listOf(
                            Free,
                            Wheelchair,
                            ResultFilter.Toggle("changing", Res.string.filter_baby_changing, Icons.Default.BabyChangingStation) {
                                details,
                                _,
                                ->
                                PlaceFeature.CHANGING_TABLE in details.features
                            },
                            OpenNow,
                        ),
                )
            PlaceCategory.HOTELS ->
                CategoryProfile(
                    kind = {
                        place,
                        details,
                        ->
                        listOfNotNull(place.category?.toCategoryLabel(), details?.stars?.let { "$it★" }).joinToString(" ")
                    },
                    icons = { icons(it, wheelchair = true) },
                    filters =
                        listOf(
                            ResultFilter.Toggle("stars3", Res.string.filter_stars, labelArgs = listOf(3)) { details, _ ->
                                (
                                    details.stars
                                        ?: 0
                                ) >=
                                    3
                            },
                            ResultFilter.Toggle("stars4", Res.string.filter_stars, labelArgs = listOf(4)) { details, _ ->
                                (
                                    details.stars
                                        ?: 0
                                ) >=
                                    4
                            },
                            Wheelchair,
                        ),
                )
            PlaceCategory.GAS_STATIONS ->
                CategoryProfile(
                    kind = { place, details ->
                        listOfNotNull(
                            details?.brand?.takeIf { it != place.name },
                            details?.fuels?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label },
                        ).joinToString(" · ")
                    },
                    filters =
                        listOf(
                            OpenNow,
                            ResultFilter.Choice("fuel", Res.string.filter_fuel, values = { details -> details.fuels.map { it.label } }),
                            Chain,
                        ),
                )
            PlaceCategory.CHARGING ->
                CategoryProfile(
                    kind = { _, details ->
                        details?.let {
                            listOfNotNull(
                                it.operator ?: it.brand,
                                it.maxPowerKw?.let { kw -> "$kw kW" },
                                it.connectors.takeIf { list -> list.isNotEmpty() }?.joinToString(", "),
                            ).joinToString(" · ")
                        }
                    },
                    facts = { details -> listOfNotNull(Res.string.fact_free.takeIf { details.free == true }) },
                    filters =
                        listOf(
                            ResultFilter.Choice("connector", Res.string.filter_connector, values = { it.connectors }),
                            ResultFilter.Toggle("fast", Res.string.filter_fast_charging, labelArgs = listOf(FAST_CHARGING_KW)) {
                                details,
                                _,
                                ->
                                (details.maxPowerKw ?: 0) >= FAST_CHARGING_KW
                            },
                            Free,
                        ),
                )
            PlaceCategory.SHARED_VEHICLES ->
                CategoryProfile(kind = {
                    place,
                    _,
                    ->
                    place.category?.toCategoryLabel()
                }, filters = emptyList())
            PlaceCategory.ATTRACTIONS ->
                CategoryProfile(
                    kind = { place, _ -> place.category?.toCategoryLabel() },
                    facts = { details -> listOfNotNull(Res.string.fact_free_entry.takeIf { details.free == true }) },
                    icons = { icons(it, wheelchair = true) },
                    filters =
                        listOf(
                            OpenNow,
                            ResultFilter.Toggle("free", Res.string.filter_free_entry) { details, _ -> details.free == true },
                        ),
                    showsDescription = true,
                )
        }

@Composable
internal fun ResultFilter.Toggle.text(): String = stringResource(label, *labelArgs.toTypedArray())

private val OpenNow =
    ResultFilter.Toggle("open", Res.string.filter_open_now) { details, now ->
        val hours = details.openingHours?.let(OpeningHours::parse) ?: return@Toggle false
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        hours.status(local.dayOfWeek.ordinal, local.hour * 60 + local.minute) is OpenStatus.Open
    }

private val Wheelchair =
    ResultFilter.Toggle("wheelchair", Res.string.filter_wheelchair, Icons.AutoMirrored.Filled.Accessible) { details, _ ->
        details.wheelchair == Accessibility.YES
    }

private val OutdoorSeating =
    ResultFilter.Toggle("outdoor", Res.string.filter_outdoor_seating, Icons.Default.Deck) { details, _ ->
        PlaceFeature.OUTDOOR_SEATING in details.features
    }

private val WiFi =
    ResultFilter.Toggle("wifi", Res.string.filter_wifi, Icons.Default.Wifi) { details, _ -> PlaceFeature.WIFI in details.features }

private val Chain = ResultFilter.Choice("chain", Res.string.filter_chain, values = { listOfNotNull(it.brand) })

private val Free = ResultFilter.Toggle("free", Res.string.filter_free) { details, _ -> details.free == true }

private fun cuisineOrType(
    place: SearchResult.Place,
    details: PlaceDetails?,
): String? =
    details?.cuisine?.take(2)?.joinToString(", ") { it.toCategoryLabel() }?.takeIf { it.isNotEmpty() }
        ?: place.category?.toCategoryLabel()

private fun icons(
    details: PlaceDetails,
    wheelchair: Boolean = false,
    outdoor: Boolean = false,
    takeaway: Boolean = false,
    wifi: Boolean = false,
    changingTable: Boolean = false,
): List<ImageVector> =
    listOfNotNull(
        Icons.AutoMirrored.Filled.Accessible.takeIf { wheelchair && details.wheelchair == Accessibility.YES },
        Icons.Default.Deck.takeIf { outdoor && PlaceFeature.OUTDOOR_SEATING in details.features },
        Icons.Default.TakeoutDining.takeIf { takeaway && PlaceFeature.TAKEAWAY in details.features },
        Icons.Default.Wifi.takeIf { wifi && PlaceFeature.WIFI in details.features },
        Icons.Default.BabyChangingStation.takeIf { changingTable && PlaceFeature.CHANGING_TABLE in details.features },
    )

private val PlaceDetails.bank: String?
    get() = operator ?: brand

private val PlaceDetails.connectors: List<String>
    get() = chargers.map { it.connector.label }.distinct()

private val PlaceDetails.maxPowerKw: Int?
    get() = chargers.mapNotNull { it.powerKw }.maxOrNull()

private const val FAST_CHARGING_KW = 50

private val Connector.label: String
    get() =
        when (this) {
            Connector.CCS -> "CCS"
            Connector.TYPE_2 -> "Type 2"
            Connector.CHADEMO -> "CHAdeMO"
            Connector.CCS1 -> "CCS1"
            Connector.TYPE_1 -> "Type 1"
            Connector.TESLA -> "Tesla"
            Connector.SCHUKO -> "Schuko"
            Connector.CEE -> "CEE"
        }

private val Fuel.label: String
    get() =
        when (this) {
            Fuel.DIESEL -> "Diesel"
            Fuel.PETROL_95 -> "Super 95"
            Fuel.E10 -> "E10"
            Fuel.PETROL_98 -> "Super 98"
            Fuel.LPG -> "LPG"
            Fuel.CNG -> "CNG"
        }
