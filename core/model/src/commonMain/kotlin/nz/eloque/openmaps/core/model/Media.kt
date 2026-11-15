package nz.eloque.openmaps.core.model

data class PlacePhoto(
    val url: String,
    /** The photo's page with its full size, author and licence. */
    val pageUrl: String,
    val credit: String?,
)

data class PlaceArticle(
    val title: String,
    val extract: String,
    val url: String,
)

data class PlaceMedia(
    val photos: List<PlacePhoto>,
    /** True when [photos] were taken near the place rather than linked to it, so they may show something else. */
    val photosNearby: Boolean,
    val article: PlaceArticle?,
)

interface PlaceMediaSource {
    /** Photos and an encyclopedia summary for the place, falling back to photos taken in or near [bounds], or near [location]. */
    suspend fun media(
        links: MediaLinks,
        location: LatLng,
        bounds: GeoBounds?,
    ): PlaceMedia
}
