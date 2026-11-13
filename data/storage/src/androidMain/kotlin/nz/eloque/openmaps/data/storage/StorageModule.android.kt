package nz.eloque.openmaps.data.storage

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformStorageModule: Module =
    module {
        single<RoomDatabase.Builder<OpenMapsDatabase>> {
            val context = androidContext()
            Room.databaseBuilder<OpenMapsDatabase>(context, context.getDatabasePath("openmaps.db").absolutePath)
        }
        single<SQLiteDriver> { BundledSQLiteDriver() }
    }
