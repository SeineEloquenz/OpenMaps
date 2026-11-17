package nz.eloque.openmaps.data.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.PlaceList
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.RouteEnd
import nz.eloque.openmaps.core.model.SavedRoute
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.storageKey
import kotlin.time.Clock

internal class RoomPlacesStore(
    private val dao: PlacesDao,
) : PlacesStore {
    override val favourites: Flow<List<Favourite>> =
        dao.favourites().map { rows ->
            rows.map { row ->
                val label = FavouriteLabel.entries.firstOrNull { it.name == row.label } ?: FavouriteLabel.OTHER
                Favourite(row.id, label, row.place.toSearchResult(), row.customName)
            }
        }

    override val recents: Flow<List<SearchResult>> =
        dao.recents(SHOWN_RECENTS).map { rows -> rows.map { it.place.toSearchResult() } }

    override suspend fun saveFavourite(
        place: SearchResult,
        label: FavouriteLabel,
    ) {
        val entity = FavouriteEntity(label = label.name, place = place.toColumns())
        if (label == FavouriteLabel.OTHER) dao.insertFavourite(entity) else dao.replaceLabel(entity)
    }

    override suspend fun removeFavourite(id: Long) = dao.deleteFavourite(id)

    override suspend fun addRecent(place: SearchResult) {
        dao.upsertRecent(RecentEntity(place.storageKey, place.toColumns(), Clock.System.now().toEpochMilliseconds()))
        dao.trimRecents(KEPT_RECENTS)
    }

    override suspend fun renameFavourite(
        id: Long,
        name: String?,
    ) = dao.renameFavourite(id, name?.trim()?.takeIf { it.isNotEmpty() })

    override val savedRoutes: Flow<List<SavedRoute>> =
        dao.savedRoutes().map { rows -> rows.map { SavedRoute(it.id, it.from?.toRouteEnd(), it.to?.toRouteEnd()) } }

    override suspend fun saveRoute(
        from: RouteEnd?,
        to: RouteEnd?,
    ) {
        dao.insertRoute(SavedRouteEntity(from = from?.toColumns(), to = to?.toColumns()))
    }

    override suspend fun removeRoute(id: Long) = dao.deleteRoute(id)

    override val lists: Flow<List<PlaceList>> =
        combine(dao.lists(), dao.listEntries()) { lists, entries ->
            val byList = entries.groupBy { it.listId }
            lists.map { list -> PlaceList(list.id, list.name, byList[list.id].orEmpty().map { it.place.toSearchResult() }, list.icon) }
        }

    override suspend fun createList(
        name: String,
        icon: String?,
    ): Long = dao.insertList(PlaceListEntity(name = name.trim(), icon = icon))

    override suspend fun updateList(
        id: Long,
        name: String,
        icon: String?,
    ) = dao.updateList(id, name.trim(), icon)

    override suspend fun deleteList(id: Long) = dao.deleteList(id)

    override suspend fun addToList(
        listId: Long,
        place: SearchResult,
    ) = dao.insertListEntry(ListEntryEntity(listId = listId, placeKey = place.storageKey, place = place.toColumns()))

    override suspend fun removeFromList(
        listId: Long,
        place: SearchResult,
    ) = dao.deleteListEntry(listId, place.storageKey)

    private companion object {
        const val SHOWN_RECENTS = 15
        const val KEPT_RECENTS = 50
    }
}
