package nz.eloque.openmaps.data.reviews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceReviews
import nz.eloque.openmaps.core.model.Review
import nz.eloque.openmaps.core.model.Reviews
import kotlin.math.roundToInt
import kotlin.time.Instant

@Serializable
internal data class ReviewsDto(
    val reviews: List<SignedReviewDto> = emptyList(),
)

@Serializable
internal data class SignedReviewDto(
    val signature: String,
    val payload: PayloadDto,
)

@Serializable
internal data class PayloadDto(
    val iat: Long,
    val rating: Int? = null,
    val opinion: String? = null,
    val metadata: MetadataDto? = null,
)

@Serializable
internal data class MetadataDto(
    val nickname: String? = null,
)

/** Reviews from a Mangrove server, which finds places by name within [UNCERTAINTY_METRES] of a point. */
internal class MangroveReviews(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
) : Reviews {
    override suspend fun of(
        name: String,
        location: LatLng,
    ): PlaceReviews {
        val base = baseUrl.first()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return PlaceReviews(emptyList(), ATTRIBUTION)
        val reviews =
            client
                .get("$base/reviews") { parameter("sub", subject(name, location)) }
                .body<ReviewsDto>()
                .reviews
                .mapNotNull { it.toReview() }
                .sortedByDescending { it.time }
        return PlaceReviews(reviews, ATTRIBUTION)
    }
}

internal fun subject(
    name: String,
    location: LatLng,
) = "geo:${location.latitude},${location.longitude}?q=${name.encodeURLParameter()}&u=$UNCERTAINTY_METRES"

internal fun SignedReviewDto.toReview(): Review? {
    val opinion = payload.opinion?.trim()?.takeIf { it.isNotEmpty() }
    // Mangrove rates from 0 to 100, where each of the five stars is 25 apart.
    val stars = payload.rating?.let { (it / 25.0).roundToInt().coerceIn(0, 4) + 1 }
    if (opinion == null && stars == null) return null
    return Review(
        id = signature,
        stars = stars,
        opinion = opinion,
        author = payload.metadata?.nickname?.trim()?.takeIf { it.isNotEmpty() },
        time = Instant.fromEpochSeconds(payload.iat),
    )
}

private const val UNCERTAINTY_METRES = 50
private const val ATTRIBUTION = "<a href=\"https://mangrove.reviews\">Mangrove</a>, CC BY 4.0"
