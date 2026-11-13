package nz.eloque.openmaps.data.storage

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import nz.eloque.openmaps.core.model.PlacesStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the platform's [RoomDatabase.Builder] and [SQLiteDriver]. */
internal expect val platformStorageModule: Module

val storageModule =
    module {
        includes(platformStorageModule)
        single<OpenMapsDatabase> {
            get<RoomDatabase.Builder<OpenMapsDatabase>>()
                .setDriver(get<SQLiteDriver>())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        single<PlacesStore> { RoomPlacesStore(get<OpenMapsDatabase>().placesDao()) }
    }
