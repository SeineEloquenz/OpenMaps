package nz.eloque.openmaps.car

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.DistanceSpan
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SearchResult

/** Where a trip from the car should go, either known or still to be searched for. */
internal sealed interface CarDestination {
    data class At(
        val name: String,
        val location: LatLng,
        /** The search result it came from, remembered among the recents once the trip starts. */
        val place: SearchResult? = null,
    ) : CarDestination

    data class Search(
        val query: String,
    ) : CarDestination
}

internal fun SearchResult.toDestination() = CarDestination.At(name, location, this)

/** Plans a route to [destination], or while a trip runs adds it as the next stop. */
internal fun Screen.choose(
    home: HomeScreen,
    destination: CarDestination.At,
) {
    val navigator = home.navigator
    if (navigator.state.value == null) {
        screenManager.push(RoutePreviewScreen(carContext, home, destination))
        return
    }
    lifecycleScope.launch {
        try {
            navigator.addStop(destination.location)
            screenManager.popToRoot()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CarToast.makeText(carContext, R.string.car_no_route, CarToast.LENGTH_LONG).show()
        }
    }
}

/** How many rows the car allows in a list right now, which is fewer while driving. */
internal fun CarContext.listLimit(): Int =
    runCatching {
        getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
    }.getOrDefault(DEFAULT_LIST_LIMIT)

/** A distance shown in the car's own format, as a span over a placeholder. */
internal fun distanceText(
    meters: Double,
    unit: DistanceUnit,
): CharSequence =
    SpannableString(" ").apply {
        setSpan(DistanceSpan.create(distance(meters, unit)), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

/** Runs [onShown] each time [screen] comes to the front and [onHidden] when another covers it or it closes. */
internal fun whileShown(
    screen: Screen,
    onShown: () -> Unit,
    onHidden: () -> Unit,
) {
    screen.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onShown()

            override fun onStop(owner: LifecycleOwner) = onHidden()
        },
    )
}

private const val DEFAULT_LIST_LIMIT = 6
