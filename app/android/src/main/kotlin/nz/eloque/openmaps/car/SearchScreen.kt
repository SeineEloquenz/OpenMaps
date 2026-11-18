package nz.eloque.openmaps.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Searches places and addresses near the car, which the car only allows while parked. */
internal class SearchScreen(
    carContext: CarContext,
    private val home: HomeScreen,
) : Screen(carContext),
    KoinComponent {
    private val geocoder: Geocoder by inject()
    private val settings: SettingsStore by inject()
    private var results: List<SearchResult> = emptyList()
    private var searched = false
    private var loading = false
    private var job: Job? = null

    override fun onGetTemplate(): Template {
        val builder =
            SearchTemplate.Builder(
                object : SearchTemplate.SearchCallback {
                    override fun onSearchTextChanged(searchText: String) = search(searchText, PAUSE_MILLIS)

                    override fun onSearchSubmitted(searchText: String) = search(searchText, 0)
                },
            ).setHeaderAction(Action.BACK)
                .setSearchHint(carContext.getString(R.string.car_search_hint))
                .setShowKeyboardByDefault(true)
        if (loading) return builder.setLoading(true).build()
        val list = ItemList.Builder()
        if (searched && results.isEmpty()) list.setNoItemsMessage(carContext.getString(R.string.car_nothing_found))
        val unit = settings.distanceUnit.value
        results.take(carContext.listLimit()).forEach { result ->
            list.addItem(
                Row.Builder()
                    .setTitle(result.name)
                    .apply {
                        val distance = home.position?.distanceTo(result.location)?.let { distanceText(it, unit) }
                        val area = result.area
                        when {
                            distance != null && area != null ->
                                addText(
                                    android.text.SpannableStringBuilder(distance).append(" · ").append(area),
                                )
                            distance != null -> addText(distance)
                            area != null -> addText(area)
                        }
                    }.setOnClickListener { choose(home, result.toDestination()) }
                    .build(),
            )
        }
        return builder.setItemList(list.build()).build()
    }

    private fun search(
        text: String,
        pause: Long,
    ) {
        job?.cancel()
        if (text.isBlank()) {
            results = emptyList()
            searched = false
            invalidate()
            return
        }
        job =
            lifecycleScope.launch {
                delay(pause)
                loading = true
                invalidate()
                results =
                    try {
                        geocoder.search(text, home.position)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                searched = true
                loading = false
                invalidate()
            }
    }
}

private const val PAUSE_MILLIS = 600L
