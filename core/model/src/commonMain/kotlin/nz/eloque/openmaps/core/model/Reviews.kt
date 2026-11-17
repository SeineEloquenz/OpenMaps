package nz.eloque.openmaps.core.model

import kotlin.time.Instant

data class Review(
    val id: String,
    /** One to five stars, null when the review only has text. */
    val stars: Int?,
    val opinion: String?,
    val author: String?,
    val time: Instant,
)

data class PlaceReviews(
    /** Newest first. */
    val reviews: List<Review>,
    /** Where the reviews come from, as HTML. */
    val attribution: String,
) {
    val rated: List<Int> get() = reviews.mapNotNull { it.stars }
    val average: Double? get() = rated.takeIf { it.isNotEmpty() }?.average()
}

interface Reviews {
    suspend fun of(
        name: String,
        location: LatLng,
    ): PlaceReviews
}
