package nz.eloque.openmaps.car

import android.text.SpannableStringBuilder
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.NearbyPlace
import nz.eloque.openmaps.core.model.NearbyPlaces
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.category.label
import nz.eloque.openmaps.ui.components.formatPrice
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Gas stations or chargers around the car, numbered on the map, with prices or power where known. */
internal class NearbyScreen(
    carContext: CarContext,
    private val home: HomeScreen,
    private val category: PlaceCategory,
    private val title: String,
) : Screen(carContext),
    KoinComponent {
    private val nearby: NearbyPlaces by inject()
    private val settings: SettingsStore by inject()
    private var places: List<NearbyPlace>? = null
    private var failed = false

    init {
        val center = home.position
        if (center == null) {
            failed = true
        } else {
            val area =
                GeoBounds(
                    center.latitude,
                    center.longitude,
                    center.latitude,
                    center.longitude,
                ).coveringAtLeast(center, RADIUS_METERS)
            lifecycleScope.launch {
                nearby.search(category, center, area, loaded = null).catch { failed = true }.collect { results ->
                    places = results.places.take(carContext.listLimit())
                    showOnMap()
                    invalidate()
                }
                if (places == null) failed = true
                invalidate()
            }
        }
        whileShown(this, onShown = ::showOnMap, onHidden = { home.mapLent = false })
    }

    private fun showOnMap() {
        val shown = places ?: return
        home.mapLent = true
        home.map.lines = emptyList()
        home.map.markers = shown.map { it.place.location }
        home.map.fit(shown.map { it.place.location } + listOfNotNull(home.position))
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val builder = PlaceListNavigationTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK)
        val shown = places
        if (shown == null && !failed) return builder.setLoading(true).build()
        val list = ItemList.Builder()
        if (shown.isNullOrEmpty()) {
            list.setNoItemsMessage(
                carContext.getString(
                    if (home.position ==
                        null
                    ) {
                        R.string.car_no_location
                    } else {
                        R.string.car_nothing_found
                    },
                ),
            )
        }
        val unit = settings.distanceUnit.value
        val fuel = settings.preferredFuel.value
        shown.orEmpty().forEachIndexed { index, item ->
            val text = SpannableStringBuilder("${index + 1} · ")
            home.position?.let { text.append(distanceText(it.distanceTo(item.place.location), unit)) }
            val prices = item.details?.fuelPrices.orEmpty()
            (prices.firstOrNull { it.fuel == fuel } ?: prices.firstOrNull())?.let {
                text.append(" · ${it.fuel.label} ${formatPrice(it.price, it.currency)}")
            }
            item.details?.chargers?.mapNotNull { it.powerKw }?.maxOrNull()?.let { text.append(" · $it kW") }
            list.addItem(
                Row.Builder()
                    .setTitle(item.details?.brand?.takeIf { item.place.name.isBlank() } ?: item.place.name)
                    .addText(text)
                    .setOnClickListener { choose(home, CarDestination.At(item.place.name, item.place.location, item.place)) }
                    .build(),
            )
        }
        return builder.setItemList(list.build()).build()
    }
}

private const val RADIUS_METERS = 5_000.0
