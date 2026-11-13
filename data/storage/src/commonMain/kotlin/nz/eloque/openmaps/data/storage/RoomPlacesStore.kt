package nz.eloque.openmaps.data.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.PlacesStore
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
                Favourite(row.id, label, row.place.toSearchResult())
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

    private companion object {
        const val SHOWN_RECENTS = 15
        const val KEPT_RECENTS = 50
    }
}
