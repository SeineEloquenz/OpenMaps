package nz.eloque.openmaps

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.ServiceCatalog
import nz.eloque.openmaps.core.platform.ReminderScheduler
import nz.eloque.openmaps.core.platform.Sharer
import nz.eloque.openmaps.core.platform.createHttpClient
import nz.eloque.openmaps.data.geocoding.geocodingModule
import nz.eloque.openmaps.data.geocoding.geocodingServiceSettings
import nz.eloque.openmaps.data.routing.routingModule
import nz.eloque.openmaps.data.routing.routingServiceSettings
import nz.eloque.openmaps.data.storage.storageModule
import nz.eloque.openmaps.data.transit.TransitStreetRouter
import nz.eloque.openmaps.data.transit.transitModule
import nz.eloque.openmaps.data.transit.transitServiceSettings
import nz.eloque.openmaps.navigation.NavigationService
import nz.eloque.openmaps.reminder.AndroidReminderScheduler
import nz.eloque.openmaps.ui.uiModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.Locale

class OpenMapsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val reminders = AndroidReminderScheduler(this)
        reminders.restore()
        startKoin {
            androidContext(this@OpenMapsApplication)
            modules(
                module {
                    single { createHttpClient(USER_AGENT) }
                    single<ReminderScheduler> { reminders }
                    single<LocationUpdates> { AndroidLocationUpdates(this@OpenMapsApplication) }
                    single { ServiceCatalog(transitServiceSettings + routingServiceSettings + geocodingServiceSettings) }
                    single<Sharer> {
                        Sharer { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
                transitModule(language = { Locale.getDefault().language }),
                routingModule(fallback = { get<TransitStreetRouter>() }, language = { Locale.getDefault().toLanguageTag() }),
                geocodingModule(language = { Locale.getDefault().language }),
                storageModule,
                uiModule,
            )
        }
        MainScope().launch {
            get<Navigator>()
                .state
                .map { it != null }
                .distinctUntilChanged()
                .filter { it }
                .collect { NavigationService.start(this@OpenMapsApplication) }
        }
    }

    private companion object {
        const val USER_AGENT = "OpenMaps/${BuildConfig.VERSION_NAME} (https://github.com/SeineEloquenz/openmaps)"
    }
}
