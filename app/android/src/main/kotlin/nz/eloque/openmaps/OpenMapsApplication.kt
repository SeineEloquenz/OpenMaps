package nz.eloque.openmaps

import android.app.Application
import nz.eloque.openmaps.core.platform.createHttpClient
import nz.eloque.openmaps.data.geocoding.geocodingModule
import nz.eloque.openmaps.data.transit.transitModule
import nz.eloque.openmaps.ui.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.Locale

class OpenMapsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenMapsApplication)
            modules(
                module { single { createHttpClient(USER_AGENT) } },
                transitModule(language = { Locale.getDefault().language }),
                geocodingModule(),
                uiModule,
            )
        }
    }

    private companion object {
        const val USER_AGENT = "OpenMaps/${BuildConfig.VERSION_NAME}"
    }
}
