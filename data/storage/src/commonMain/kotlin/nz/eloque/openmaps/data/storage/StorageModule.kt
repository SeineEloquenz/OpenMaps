package nz.eloque.openmaps.data.storage

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TrackStore
import nz.eloque.openmaps.core.model.TripWatchStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the platform's [RoomDatabase.Builder], [SQLiteDriver] and key-value Settings. */
internal expect val platformStorageModule: Module

val storageModule =
    module {
        includes(platformStorageModule)
        single<OpenMapsDatabase> {
            get<RoomDatabase.Builder<OpenMapsDatabase>>()
                .setDriver(get<SQLiteDriver>())
                .addMigrations(PlaceIdMigration)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        single<PlacesStore> { RoomPlacesStore(get<OpenMapsDatabase>().placesDao()) }
        single<SettingsStore> { PreferencesSettingsStore(get()) }
        single<TrackStore> { RoomTrackStore(get<OpenMapsDatabase>().tracksDao()) }
        single<TripWatchStore> { RoomTripWatchStore(get<OpenMapsDatabase>().tripWatchDao()) }
    }
