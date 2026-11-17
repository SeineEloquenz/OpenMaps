package nz.eloque.openmaps.data.storage

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlacesDao {
    @Query(
        "SELECT * FROM favourite ORDER BY CASE label WHEN 'HOME' THEN 0 WHEN 'WORK' THEN 1 ELSE 2 END, id",
    )
    abstract fun favourites(): Flow<List<FavouriteEntity>>

    @Insert
    abstract suspend fun insertFavourite(favourite: FavouriteEntity): Long

    @Query("DELETE FROM favourite WHERE label = :label")
    abstract suspend fun deleteLabel(label: String)

    @Query("DELETE FROM favourite WHERE id = :id")
    abstract suspend fun deleteFavourite(id: Long)

    @Transaction
    open suspend fun replaceLabel(favourite: FavouriteEntity) {
        deleteLabel(favourite.label)
        insertFavourite(favourite)
    }

    @Query("UPDATE favourite SET customName = :name WHERE id = :id")
    abstract suspend fun renameFavourite(
        id: Long,
        name: String?,
    )

    @Query("SELECT * FROM saved_route ORDER BY id DESC")
    abstract fun savedRoutes(): Flow<List<SavedRouteEntity>>

    @Insert
    abstract suspend fun insertRoute(route: SavedRouteEntity): Long

    @Query("DELETE FROM saved_route WHERE id = :id")
    abstract suspend fun deleteRoute(id: Long)

    @Query("SELECT * FROM recent ORDER BY usedAt DESC LIMIT :limit")
    abstract fun recents(limit: Int): Flow<List<RecentEntity>>

    @Upsert
    abstract suspend fun upsertRecent(recent: RecentEntity)

    @Query("DELETE FROM recent WHERE `key` NOT IN (SELECT `key` FROM recent ORDER BY usedAt DESC LIMIT :keep)")
    abstract suspend fun trimRecents(keep: Int)

    @Query("SELECT * FROM place_list ORDER BY name COLLATE NOCASE")
    abstract fun lists(): Flow<List<PlaceListEntity>>

    @Query("SELECT * FROM list_entry ORDER BY id")
    abstract fun listEntries(): Flow<List<ListEntryEntity>>

    @Insert
    abstract suspend fun insertList(list: PlaceListEntity): Long

    @Query("UPDATE place_list SET name = :name, icon = :icon WHERE id = :id")
    abstract suspend fun updateList(
        id: Long,
        name: String,
        icon: String?,
    )

    @Query("DELETE FROM place_list WHERE id = :id")
    abstract suspend fun deleteListRow(id: Long)

    @Query("DELETE FROM list_entry WHERE listId = :listId")
    abstract suspend fun deleteListEntries(listId: Long)

    @Transaction
    open suspend fun deleteList(id: Long) {
        deleteListEntries(id)
        deleteListRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertListEntry(entry: ListEntryEntity)

    @Query("DELETE FROM list_entry WHERE listId = :listId AND placeKey = :placeKey")
    abstract suspend fun deleteListEntry(
        listId: Long,
        placeKey: String,
    )
}

@Database(
    entities = [FavouriteEntity::class, RecentEntity::class, SavedRouteEntity::class, PlaceListEntity::class, ListEntryEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
@ConstructedBy(OpenMapsDatabaseConstructor::class)
abstract class OpenMapsDatabase : RoomDatabase() {
    abstract fun placesDao(): PlacesDao
}

@Suppress("KotlinNoActualForExpect")
expect object OpenMapsDatabaseConstructor : RoomDatabaseConstructor<OpenMapsDatabase> {
    override fun initialize(): OpenMapsDatabase
}

/**
 * Renames the place id column and gives the OpenStreetMap ids stored before, like `way/123`, their `osm:` prefix.
 * Also drops the icon size earlier versions kept in place types, as in `museum_16`.
 */
internal val PlaceIdMigration =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            for (table in listOf("favourite", "recent")) {
                connection.execSQL("ALTER TABLE `$table` RENAME COLUMN `osmId` TO `placeId`")
                connection.execSQL(
                    "UPDATE `$table` SET `placeId` = 'osm:' || `placeId` WHERE `placeId` IS NOT NULL AND `placeId` NOT LIKE '%:%'",
                )
                connection.execSQL(
                    "UPDATE `$table` SET `category` = substr(`category`, 1, length(`category`) - 3) WHERE `category` GLOB '*_[0-9][0-9]'",
                )
            }
        }
    }
