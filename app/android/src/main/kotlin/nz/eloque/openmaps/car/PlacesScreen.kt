package nz.eloque.openmaps.car

import android.text.SpannableStringBuilder
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.storageKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Places to drive to: home and work, fuel and charging nearby, then saved and recent places. */
internal class PlacesScreen(
    carContext: CarContext,
    private val home: HomeScreen,
) : Screen(carContext),
    KoinComponent {
    private val places: PlacesStore by inject()
    private val settings: SettingsStore by inject()
    private var favourites: List<Favourite>? = null
    private var recents: List<SearchResult> = emptyList()

    init {
        lifecycleScope.launch {
            recents = places.recents.first()
            favourites = places.favourites.first()
            invalidate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val builder =
            PlaceListNavigationTemplate.Builder()
                .setTitle(carContext.getString(if (home.navigator.state.value == null) R.string.car_where_to else R.string.car_add_stop))
                .setHeaderAction(Action.BACK)
                .setActionStrip(
                    ActionStrip.Builder()
                        .addAction(
                            Action.Builder()
                                .setIcon(home.guidance.icon(R.drawable.car_search))
                                .setOnClickListener { screenManager.push(SearchScreen(carContext, home)) }
                                .build(),
                        ).build(),
                )
        val saved = favourites ?: return builder.setLoading(true).build()
        val list = ItemList.Builder()
        rows(saved).take(carContext.listLimit()).forEach(list::addItem)
        return builder.setItemList(list.build()).build()
    }

    private fun rows(saved: List<Favourite>): List<Row> =
        buildList {
            saved.firstOrNull { it.label == FavouriteLabel.HOME }?.let { add(place(it, R.drawable.car_home, R.string.car_home)) }
            saved.firstOrNull { it.label == FavouriteLabel.WORK }?.let { add(place(it, R.drawable.car_work, R.string.car_work)) }
            add(category(PlaceCategory.GAS_STATIONS, R.drawable.car_local_gas_station, R.string.car_gas))
            add(category(PlaceCategory.CHARGING, R.drawable.car_ev_station, R.string.car_charging))
            saved.filter { it.label == FavouriteLabel.OTHER }.forEach { add(place(it, R.drawable.car_star, null)) }
            val shown = saved.map { it.place.storageKey }.toSet()
            recents.filter { it.storageKey !in shown }.forEach { recent ->
                add(
                    Row.Builder()
                        .setTitle(recent.name)
                        .addText(withDistance(recent.location, recent.area))
                        .setImage(home.guidance.icon(R.drawable.car_history))
                        .setOnClickListener { choose(home, recent.toDestination()) }
                        .build(),
                )
            }
        }

    private fun place(
        favourite: Favourite,
        icon: Int,
        label: Int?,
    ): Row =
        Row.Builder()
            .setTitle(label?.let(carContext::getString) ?: favourite.displayName)
            .addText(withDistance(favourite.place.location, if (label != null) favourite.displayName else favourite.place.area))
            .setImage(home.guidance.icon(icon))
            .setOnClickListener { choose(home, CarDestination.At(favourite.displayName, favourite.place.location, favourite.place)) }
            .build()

    private fun category(
        category: PlaceCategory,
        icon: Int,
        label: Int,
    ): Row =
        Row.Builder()
            .setTitle(carContext.getString(label))
            .setImage(home.guidance.icon(icon))
            .setBrowsable(true)
            .setOnClickListener { screenManager.push(NearbyScreen(carContext, home, category, carContext.getString(label))) }
            .build()

    /** The distance from the car first, as the car requires one on every place it lists, then [detail]. */
    private fun withDistance(
        location: LatLng,
        detail: String?,
    ): CharSequence {
        val text = SpannableStringBuilder(distanceText(home.position?.distanceTo(location) ?: 0.0, settings.distanceUnit.value))
        detail?.takeIf { it.isNotBlank() }?.let { text.append(" · ").append(it) }
        return text
    }
}
