package nz.eloque.openmaps

import android.app.Application
import android.content.Intent
import nz.eloque.openmaps.core.platform.ReminderScheduler
import nz.eloque.openmaps.core.platform.Sharer
import nz.eloque.openmaps.core.platform.createHttpClient
import nz.eloque.openmaps.data.geocoding.geocodingModule
import nz.eloque.openmaps.data.storage.storageModule
import nz.eloque.openmaps.data.transit.transitModule
import nz.eloque.openmaps.reminder.AndroidReminderScheduler
import nz.eloque.openmaps.ui.uiModule
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
                    single<Sharer> {
                        Sharer { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
                transitModule(language = { Locale.getDefault().language }),
                geocodingModule(language = { Locale.getDefault().language }),
                storageModule,
                uiModule,
            )
        }
    }

    private companion object {
        const val USER_AGENT = "OpenMaps/${BuildConfig.VERSION_NAME} (https://github.com/SeineEloquenz/openmaps)"
    }
}
