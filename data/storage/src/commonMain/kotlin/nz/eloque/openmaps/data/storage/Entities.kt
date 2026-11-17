package nz.eloque.openmaps.data.storage

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.RouteEnd
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode

data class PlaceColumns(
    val kind: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val area: String? = null,
    val stopId: String? = null,
    val placeId: String? = null,
    val category: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val postcode: String? = null,
    /** Comma separated [TransportMode] names. */
    val modes: String = "",
)

@Entity(tableName = "favourite")
data class FavouriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    @Embedded val place: PlaceColumns,
    val customName: String? = null,
)

@Entity(tableName = "recent")
data class RecentEntity(
    @PrimaryKey val key: String,
    @Embedded val place: PlaceColumns,
    val usedAt: Long,
)

internal fun SearchResult.toColumns(): PlaceColumns {
    val lat = location.latitude
    val lon = location.longitude
    return when (this) {
        is SearchResult.Stop ->
            PlaceColumns(KIND_STOP, name, lat, lon, area = area, stopId = id, modes = modes.joinToString(","))
        is SearchResult.Place ->
            PlaceColumns(
                KIND_PLACE,
                name,
                lat,
                lon,
                area = area,
                placeId = id?.value,
                category = category,
            )
        is SearchResult.Address ->
            PlaceColumns(KIND_ADDRESS, name, lat, lon, area = area, street = street, houseNumber = houseNumber, postcode = postcode)
    }
}

/** Unknown kinds read as places and unknown modes are dropped, so rows from newer app versions never throw. */
internal fun PlaceColumns.toSearchResult(): SearchResult {
    val location = LatLng(latitude, longitude)
    return when {
        kind == KIND_STOP && stopId != null -> {
            val parsedModes = modes.split(',').mapNotNull { mode -> TransportMode.entries.firstOrNull { it.name == mode } }
            SearchResult.Stop(stopId, name, location, area, parsedModes.toSet())
        }
        kind == KIND_ADDRESS -> SearchResult.Address(name, location, area, street, houseNumber, postcode)
        else -> SearchResult.Place(placeId?.let(::PlaceId), name, location, area, category)
    }
}

private const val KIND_STOP = "stop"
private const val KIND_PLACE = "place"
private const val KIND_ADDRESS = "address"

data class RouteEndColumns(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopId: String? = null,
)

/** A null end is the user's location. */
@Entity(tableName = "saved_route")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded(prefix = "from_") val from: RouteEndColumns?,
    @Embedded(prefix = "to_") val to: RouteEndColumns?,
)

internal fun RouteEnd.toColumns() = RouteEndColumns(name, location.latitude, location.longitude, stopId)

internal fun RouteEndColumns.toRouteEnd() = RouteEnd(name, LatLng(latitude, longitude), stopId)

@Entity(tableName = "place_list")
data class PlaceListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A [nz.eloque.openmaps.core.model.PlaceList.icon] key, null for the default icon. */
    val icon: String? = null,
)

/** [placeKey] is the place's storage key, so a place is in a list at most once. */
@Entity(tableName = "list_entry", indices = [Index(value = ["listId", "placeKey"], unique = true)])
data class ListEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val placeKey: String,
    @Embedded val place: PlaceColumns,
)
