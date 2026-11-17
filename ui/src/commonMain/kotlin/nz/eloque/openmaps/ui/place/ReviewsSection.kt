package nz.eloque.openmaps.ui.place

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.HtmlStyle
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceReviews
import nz.eloque.openmaps.core.model.Review
import nz.eloque.openmaps.core.model.Reviews
import nz.eloque.openmaps.ui.components.formatDate
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.reviews
import nz.eloque.openmaps.ui.resources.reviews_all
import nz.eloque.openmaps.ui.resources.reviews_count
import nz.eloque.openmaps.ui.resources.reviews_source
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Reviews of the place, nothing while loading, when there are none or when the source fails. */
@Composable
fun ReviewsSection(
    name: String,
    location: LatLng,
) {
    val source = koinInject<Reviews>()
    val reviews by produceState<PlaceReviews?>(null, name, location) {
        value =
            try {
                source.of(name, location)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }
    val loaded = reviews?.takeIf { it.reviews.isNotEmpty() } ?: return
    var all by rememberSaveable(name, location) { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.reviews), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        loaded.average?.let { average ->
            Text(
                ((average * 10).roundToInt() / 10.0).toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 4.dp),
            )
            Stars(average.roundToInt(), 16.dp)
        }
    }
    Text(
        pluralStringResource(Res.plurals.reviews_count, loaded.reviews.size, loaded.reviews.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val shown = if (all) loaded.reviews else loaded.reviews.take(SHOWN)
    shown.forEach { ReviewItem(it) }
    if (!all && loaded.reviews.size > SHOWN) {
        TextButton(onClick = { all = true }) { Text(stringResource(Res.string.reviews_all, loaded.reviews.size)) }
    }
    val links = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val prefix = stringResource(Res.string.reviews_source)
    val attribution =
        remember(prefix, loaded.attribution, links) {
            buildAnnotatedString {
                append("$prefix ")
                append(htmlToAnnotatedString(loaded.attribution, style = HtmlStyle(textLinkStyles = links)))
            }
        }
    Text(
        attribution,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ReviewItem(review: Review) {
    var expanded by rememberSaveable(review.id) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp).animateContentSize().clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            review.stars?.let {
                Stars(it, 14.dp)
                Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                listOfNotNull(review.author, formatDate(review.time.toLocalDateTime(TimeZone.currentSystemDefault()).date))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        review.opinion?.let {
            Text(
                it,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Stars(
    count: Int,
    size: Dp,
) {
    Row {
        repeat(5) {
            Icon(
                if (it < count) Icons.Default.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size),
            )
        }
    }
}

private const val SHOWN = 3
private const val COLLAPSED_LINES = 4
