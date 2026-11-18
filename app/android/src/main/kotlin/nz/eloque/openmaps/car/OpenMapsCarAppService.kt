package nz.eloque.openmaps.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import nz.eloque.openmaps.core.model.parseCoordinates

/** Android Auto's entry point, one [CarSession] per connected car screen. */
class OpenMapsCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = CarSession()
}

private class CarSession : Session() {
    private var home: HomeScreen? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val screen = HomeScreen(carContext)
        home = screen
        navigateTo(intent)?.let {
            screens().push(screen)
            return RoutePreviewScreen(carContext, screen, it)
        }
        return screen
    }

    override fun onNewIntent(intent: Intent) {
        val screen = home ?: return
        val destination = navigateTo(intent) ?: return
        screens().popToRoot()
        screens().push(RoutePreviewScreen(carContext, screen, destination))
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        home?.onDarkModeChanged(carContext.isDarkMode)
    }

    private fun screens() = carContext.getCarService(ScreenManager::class.java)

    /** The destination of a navigation request from the car's assistant, given as a `geo:` address. */
    private fun navigateTo(intent: Intent): CarDestination? {
        if (intent.action != CarContext.ACTION_NAVIGATE) return null
        val uri = intent.data ?: return null
        val coordinates = uri.schemeSpecificPart.substringBefore('?')
        val query = uri.schemeSpecificPart.substringAfter("q=", "").substringBefore('&').takeIf { it.isNotBlank() }
        parseCoordinates(coordinates)?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let {
            return CarDestination.At(query?.substringAfter('(')?.substringBefore(')') ?: query ?: coordinates, it)
        }
        return query?.let { CarDestination.Search(it) }
    }
}
