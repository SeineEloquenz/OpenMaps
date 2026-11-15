package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.Accessibility
import nz.eloque.openmaps.core.model.Charger
import nz.eloque.openmaps.core.model.Connector
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceFeature

/** The OSM tags, as `key.value`, that make a place belong to a category. */
internal val PlaceCategory.osmTags: List<String>
    get() =
        when (this) {
            PlaceCategory.RESTAURANTS -> listOf("amenity.restaurant", "amenity.fast_food")
            PlaceCategory.CAFES -> listOf("amenity.cafe")
            PlaceCategory.SUPERMARKETS -> listOf("shop.supermarket", "shop.convenience")
            PlaceCategory.GAS_STATIONS -> listOf("amenity.fuel")
            PlaceCategory.CHARGING -> listOf("amenity.charging_station")
            PlaceCategory.BARS -> listOf("amenity.bar", "amenity.pub", "amenity.biergarten")
            PlaceCategory.ATTRACTIONS ->
                listOf(
                    "tourism.attraction",
                    "tourism.museum",
                    "tourism.gallery",
                    "tourism.viewpoint",
                    "tourism.zoo",
                    "tourism.theme_park",
                    "historic.castle",
                    "historic.monument",
                )
            PlaceCategory.HOTELS -> listOf("tourism.hotel", "tourism.hostel", "tourism.guest_house", "tourism.motel")
            PlaceCategory.PHARMACIES -> listOf("amenity.pharmacy")
            PlaceCategory.BAKERIES -> listOf("shop.bakery", "shop.pastry")
            PlaceCategory.ATMS -> listOf("amenity.atm")
            PlaceCategory.TOILETS -> listOf("amenity.toilets")
        }

/** First value of the first of [keys] present, for tags that may list several values separated by `;`. */
internal fun Map<String, String>.first(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { this[it] }?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

internal fun Map<String, String>.accessibility(): Accessibility? =
    when (this["wheelchair"]) {
        "yes", "designated" -> Accessibility.YES
        "limited" -> Accessibility.LIMITED
        "no" -> Accessibility.NO
        else -> null
    }

internal fun Map<String, String>.free(): Boolean? =
    when (this["fee"]) {
        "no" -> true
        "yes" -> false
        else -> null
    }

/** Hotel stars, ignoring a superior marker as in `4S`. */
internal fun Map<String, String>.stars(): Int? = this["stars"]?.takeWhile { it.isDigit() }?.toIntOrNull()

internal fun Map<String, String>.cuisine(): List<String> =
    this["cuisine"]?.split(';')?.map {
        it.trim()
    }?.filter { it.isNotEmpty() }.orEmpty()

internal fun Map<String, String>.fuels(): Set<Fuel> = FuelTags.filter { (key, _) -> this[key] == "yes" }.map { it.second }.toSet()

/** Chargers from `socket:<type>` counts and `socket:<type>:output` powers. */
internal fun Map<String, String>.chargers(): List<Charger> =
    SocketTags.mapNotNull { (socket, connector) ->
        val count = this["socket:$socket"]?.takeUnless { it == "no" || it == "0" } ?: return@mapNotNull null
        val power =
            this["socket:$socket:output"]?.let { value ->
                KilowattPattern.findAll(value).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.maxOrNull()
            }
        Charger(connector, power?.toInt(), count.toIntOrNull())
    }

private val Yes = setOf("yes", "only", "designated")

internal fun Map<String, String>.features(): Set<PlaceFeature> {
    fun yes(vararg keys: String) = keys.any { this[it] in Yes }
    return buildSet {
        if (yes("emergency")) add(PlaceFeature.EMERGENCY)
        if (yes("takeaway")) add(PlaceFeature.TAKEAWAY)
        if (yes("delivery")) add(PlaceFeature.DELIVERY)
        if (yes("outdoor_seating")) add(PlaceFeature.OUTDOOR_SEATING)
        if (this@features["internet_access"] in setOf("wlan", "wifi", "yes")) add(PlaceFeature.WIFI)
        if (yes("diet:vegetarian")) add(PlaceFeature.VEGETARIAN)
        if (yes("diet:vegan")) add(PlaceFeature.VEGAN)
        if (yes("diet:gluten_free")) add(PlaceFeature.GLUTEN_FREE)
        if (yes("payment:cards", "payment:credit_cards", "payment:debit_cards")) add(PlaceFeature.CARDS)
        if (yes("toilets")) add(PlaceFeature.TOILETS)
        if (yes("cash_in")) add(PlaceFeature.CASH_DEPOSIT)
        if (yes("changing_table")) add(PlaceFeature.CHANGING_TABLE)
    }
}

private val KilowattPattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*kW", RegexOption.IGNORE_CASE)

private val FuelTags =
    listOf(
        "fuel:diesel" to Fuel.DIESEL,
        "fuel:octane_95" to Fuel.PETROL_95,
        "fuel:e10" to Fuel.E10,
        "fuel:octane_98" to Fuel.PETROL_98,
        "fuel:lpg" to Fuel.LPG,
        "fuel:cng" to Fuel.CNG,
    )

private val SocketTags =
    listOf(
        "type2_combo" to Connector.CCS,
        "type2" to Connector.TYPE_2,
        "type2_cable" to Connector.TYPE_2,
        "chademo" to Connector.CHADEMO,
        "type1_combo" to Connector.CCS1,
        "type1" to Connector.TYPE_1,
        "tesla_supercharger" to Connector.TESLA,
        "tesla_destination" to Connector.TESLA,
        "schuko" to Connector.SCHUKO,
        "cee_blue" to Connector.CEE,
    )
