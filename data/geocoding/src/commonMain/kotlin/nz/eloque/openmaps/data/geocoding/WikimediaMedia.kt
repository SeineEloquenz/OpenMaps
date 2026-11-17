package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MediaLinks
import nz.eloque.openmaps.core.model.PlaceArticle
import nz.eloque.openmaps.core.model.PlaceGuide
import nz.eloque.openmaps.core.model.PlaceMedia
import nz.eloque.openmaps.core.model.PlaceMediaSource
import nz.eloque.openmaps.core.model.PlacePhoto
import kotlin.math.PI
import kotlin.math.cos

const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"
const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"

private const val IMAGE = "P18"
private const val COMMONS_CATEGORY = "P373"

private const val MAX_PHOTOS = 8
private const val NEARBY_RADIUS_METERS = 100

/** How far around a place's outline nearby photos may be taken, about the width of a street. */
private const val MARGIN_METERS = 50.0
private const val METERS_PER_DEGREE = 111_320.0
private const val THUMB_WIDTH = 960
private val PhotoTypes = setOf("image/jpeg", "image/png", "image/webp")

@Serializable
internal data class ClaimsDto(
    val claims: Map<String, List<ClaimDto>> = emptyMap(),
)

@Serializable
internal data class ClaimDto(
    val mainsnak: SnakDto,
)

@Serializable
internal data class SnakDto(
    val datavalue: DataValueDto? = null,
)

@Serializable
internal data class DataValueDto(
    val value: JsonElement,
)

@Serializable
internal data class EntitiesDto(
    val entities: Map<String, EntityDto> = emptyMap(),
)

@Serializable
internal data class EntityDto(
    val sitelinks: Map<String, SitelinkDto> = emptyMap(),
)

@Serializable
internal data class SitelinkDto(
    val title: String,
)

@Serializable
internal data class CommonsQueryDto(
    val query: CommonsPagesDto? = null,
)

@Serializable
internal data class CommonsPagesDto(
    val pages: List<CommonsPageDto> = emptyList(),
)

@Serializable
internal data class CommonsPageDto(
    val title: String,
    val imageinfo: List<ImageInfoDto> = emptyList(),
    val coordinates: List<CoordinateDto> = emptyList(),
)

@Serializable
internal data class ImageInfoDto(
    val thumburl: String? = null,
    val descriptionurl: String,
    val mime: String? = null,
    val extmetadata: Map<String, MetadataValueDto> = emptyMap(),
)

@Serializable
internal data class MetadataValueDto(
    val value: String,
)

@Serializable
internal data class CoordinateDto(
    val lat: Double,
    val lon: Double,
)

@Serializable
internal data class SummaryDto(
    val type: String? = null,
    val title: String,
    val extract: String = "",
    @SerialName("content_urls") val contentUrls: ContentUrlsDto? = null,
)

@Serializable
internal data class ContentUrlsDto(
    val mobile: PageUrlDto? = null,
)

@Serializable
internal data class PageUrlDto(
    val page: String,
)

/** Photos from Wikimedia Commons and Wikidata, summaries from Wikipedia and travel guide entries from Wikivoyage, in the device language. */
internal class WikimediaMedia(
    private val client: HttpClient,
    private val language: () -> String?,
) : PlaceMediaSource {
    private val articles = WikitextCache()

    override suspend fun media(
        links: MediaLinks,
        name: String?,
        location: LatLng,
        bounds: GeoBounds?,
    ): PlaceMedia =
        coroutineScope {
            val wikidata = links.wikidata?.takeIf { WikidataId.matches(it) }
            val sitelinks = async { wikidata?.let { runCatching { sitelinks(it) }.getOrNull() }.orEmpty() }
            val article = async { runCatching { article(links, sitelinks.await()) }.getOrNull() }
            val guide = async { runCatching { guide(name, location, wikidata, sitelinks.await()) }.getOrNull() }
            val images = wikidata?.let { async { wikidataStrings(it, IMAGE) } }
            val categories = wikidata?.let { async { wikidataStrings(it, COMMONS_CATEGORY) } }
            val category =
                links.commons?.takeIf { it.startsWith("Category:") }
                    ?: categories?.await()?.firstOrNull()?.let { "Category:$it" }
            val files =
                buildList {
                    links.image?.let(::commonsFileTitle)?.let(::add)
                    links.commons?.let(::commonsFileTitle)?.let(::add)
                    images?.await()?.forEach { add("File:$it") }
                    if (size < MAX_PHOTOS && category != null) addAll(runCatching { categoryFiles(category) }.getOrDefault(emptyList()))
                }.distinct().take(MAX_PHOTOS)
            val linked = if (files.isEmpty()) emptyList() else runCatching { photos(files) }.getOrDefault(emptyList())
            val nearby = if (linked.isEmpty()) runCatching { nearbyPhotos(location, bounds) }.getOrDefault(emptyList()) else emptyList()
            PlaceMedia(
                photos = linked.ifEmpty { nearby },
                photosNearby = linked.isEmpty() && nearby.isNotEmpty(),
                article = article.await(),
                guide = guide.await(),
            )
        }

    /** The string values of one Wikidata property, or nothing when the lookup fails. */
    private suspend fun wikidataStrings(
        id: String,
        property: String,
    ): List<String> =
        runCatching {
            val result: ClaimsDto =
                client
                    .get(WIKIDATA_API) {
                        parameter("action", "wbgetclaims")
                        parameter("entity", id)
                        parameter("property", property)
                        parameter("format", "json")
                        parameter("formatversion", 2)
                    }.body()
            result.claims[property].orEmpty().mapNotNull { (it.mainsnak.datavalue?.value as? JsonPrimitive)?.content }
        }.getOrDefault(emptyList())

    private suspend fun categoryFiles(category: String): List<String> {
        val result: CommonsQueryDto =
            client
                .get(COMMONS_API) {
                    parameter("action", "query")
                    parameter("generator", "categorymembers")
                    parameter("gcmtitle", category)
                    parameter("gcmtype", "file")
                    parameter("gcmlimit", MAX_PHOTOS)
                    parameter("format", "json")
                    parameter("formatversion", 2)
                }.body()
        return result.query?.pages.orEmpty().map { it.title }
    }

    private suspend fun photos(titles: List<String>): List<PlacePhoto> {
        val result: CommonsQueryDto =
            client
                .get(COMMONS_API) {
                    parameter("action", "query")
                    parameter("titles", titles.joinToString("|"))
                    imageInfo()
                }.body()
        val byTitle = result.query?.pages.orEmpty().associateBy { it.title.replace('_', ' ') }
        return titles.mapNotNull { byTitle[it.replace('_', ' ')]?.toPhoto() }
    }

    private suspend fun nearbyPhotos(
        location: LatLng,
        bounds: GeoBounds?,
    ): List<PlacePhoto> {
        val result: CommonsQueryDto =
            client
                .get(COMMONS_API) {
                    parameter("action", "query")
                    parameter("generator", "geosearch")
                    if (bounds != null) {
                        val lat = MARGIN_METERS / METERS_PER_DEGREE
                        val lon = lat / cos(location.latitude * PI / 180)
                        parameter("ggsbbox", "${bounds.north + lat}|${bounds.west - lon}|${bounds.south - lat}|${bounds.east + lon}")
                    } else {
                        parameter("ggscoord", "${location.latitude}|${location.longitude}")
                        parameter("ggsradius", NEARBY_RADIUS_METERS)
                    }
                    parameter("ggsnamespace", 6)
                    parameter("ggslimit", MAX_PHOTOS)
                    imageInfo(prop = "imageinfo|coordinates")
                }.body()
        return result.query
            ?.pages
            .orEmpty()
            .sortedBy { page -> page.coordinates.firstOrNull()?.let { LatLng(it.lat, it.lon).distanceTo(location) } ?: Double.MAX_VALUE }
            .mapNotNull { it.toPhoto() }
    }

    private fun HttpRequestBuilder.imageInfo(prop: String = "imageinfo") {
        parameter("prop", prop)
        parameter("iiprop", "url|mime|extmetadata")
        parameter("iiurlwidth", THUMB_WIDTH)
        parameter("iiextmetadatafilter", "Artist|LicenseShortName")
        parameter("format", "json")
        parameter("formatversion", 2)
    }

    private fun languages() = listOfNotNull(language()?.takeIf { it.isNotBlank() }, "en").distinct()

    /** The item's Wikipedia and Wikivoyage article titles by site, such as `dewiki` or `enwikivoyage`. */
    private suspend fun sitelinks(wikidata: String): Map<String, String> {
        val sites = languages().flatMap { listOf("${it}wiki", "${it}wikivoyage") }
        val result: EntitiesDto =
            client
                .get(WIKIDATA_API) {
                    parameter("action", "wbgetentities")
                    parameter("ids", wikidata)
                    parameter("props", "sitelinks")
                    parameter("sitefilter", sites.joinToString("|"))
                    parameter("format", "json")
                    parameter("formatversion", 2)
                }.body()
        return result.entities[wikidata]?.sitelinks.orEmpty().mapValues { it.value.title }
    }

    private suspend fun article(
        links: MediaLinks,
        sitelinks: Map<String, String>,
    ): PlaceArticle? {
        val (wiki, title) = articleTitle(links, sitelinks) ?: return null
        val summary = summary("$wiki.wikipedia.org", title)
        if (summary.type == "disambiguation" || summary.extract.isBlank()) return null
        return PlaceArticle(summary.title, summary.extract, summary.contentUrls?.mobile?.page ?: pageUrl("$wiki.wikipedia.org", title))
    }

    /** The Wikipedia language and article title, preferring the device language. */
    private fun articleTitle(
        links: MediaLinks,
        sitelinks: Map<String, String>,
    ): Pair<String, String>? {
        languages().firstNotNullOfOrNull { wiki -> sitelinks["${wiki}wiki"]?.let { wiki to it } }?.let { return it }
        val tag = links.wikipedia ?: return null
        val wiki = tag.substringBefore(':', "")
        val title = tag.substringAfter(':')
        return if (WikiCode.matches(wiki) && title.isNotBlank()) wiki to title else null
    }

    /** The place's own Wikivoyage article, or else its listing in an article about the area around it. */
    private suspend fun guide(
        name: String?,
        location: LatLng,
        wikidata: String?,
        sitelinks: Map<String, String>,
    ): PlaceGuide? {
        val wikis = languages().filter { it in Wikivoyages }
        wikis.firstNotNullOfOrNull { wiki -> sitelinks["${wiki}wikivoyage"]?.let { wiki to it } }?.let { (wiki, title) ->
            val summary = summary("$wiki.wikivoyage.org", title)
            if (summary.extract.isNotBlank()) {
                return PlaceGuide(
                    summary.title,
                    summary.extract,
                    summary.contentUrls?.mobile?.page ?: pageUrl("$wiki.wikivoyage.org", title),
                )
            }
        }
        if (name == null) return null
        for (wiki in wikis) {
            val host = "$wiki.wikivoyage.org"
            for (title in nearbyArticles(host, location)) {
                val listing = articles.get(host, title) { rawWikitext(host, title) }.firstOrNull { it.matches(name, location, wikidata) }
                if (listing != null) return PlaceGuide(title, listing.description, pageUrl(host, title))
            }
        }
        return null
    }

    private suspend fun nearbyArticles(
        host: String,
        location: LatLng,
    ): List<String> {
        val result: GeoSearchDto =
            client
                .get("https://$host/w/api.php") {
                    parameter("action", "query")
                    parameter("list", "geosearch")
                    parameter("gscoord", "${location.latitude}|${location.longitude}")
                    parameter("gsradius", GUIDE_RADIUS_METERS)
                    parameter("gslimit", GUIDE_ARTICLES)
                    parameter("format", "json")
                    parameter("formatversion", 2)
                }.body()
        return result.query?.geosearch.orEmpty().map { it.title }
    }

    private suspend fun rawWikitext(
        host: String,
        title: String,
    ): String =
        client
            .get("https://$host/w/index.php") {
                parameter("title", title)
                parameter("action", "raw")
            }.bodyAsText()

    private suspend fun summary(
        host: String,
        title: String,
    ): SummaryDto = client.get("https://$host/api/rest_v1/page/summary/${title.replace(' ', '_').encodeURLPathPart()}").body()
}

private fun pageUrl(
    host: String,
    title: String,
) = "https://$host/wiki/${title.replace(' ', '_').encodeURLPathPart()}"

/** The listings of recently read Wikivoyage articles, so places in the same town do not fetch its article again. */
private class WikitextCache {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, List<WikivoyageListing>>()

    suspend fun get(
        host: String,
        title: String,
        load: suspend () -> String,
    ): List<WikivoyageListing> {
        val key = "$host/$title"
        mutex.withLock { entries[key]?.let { return it } }
        val listings = wikivoyageListings(load())
        mutex.withLock {
            entries[key] = listings
            while (entries.size > CACHED_ARTICLES) entries.remove(entries.keys.first())
        }
        return listings
    }
}

@Serializable
internal data class GeoSearchDto(
    val query: GeoSearchResultsDto? = null,
)

@Serializable
internal data class GeoSearchResultsDto(
    val geosearch: List<GeoSearchPageDto> = emptyList(),
)

@Serializable
internal data class GeoSearchPageDto(
    val title: String,
)

private const val GUIDE_RADIUS_METERS = 10_000
private const val GUIDE_ARTICLES = 2
private const val CACHED_ARTICLES = 8

private val Wikivoyages =
    setOf(
        "bn",
        "de",
        "el",
        "en",
        "eo",
        "es",
        "fa",
        "fi",
        "fr",
        "he",
        "hi",
        "it",
        "ja",
        "nl",
        "pl",
        "ps",
        "pt",
        "ru",
        "sv",
        "tr",
        "uk",
        "vi",
        "zh",
    )

private val WikidataId = Regex("Q\\d+")
private val WikiCode = Regex("[a-z-]{2,12}")
private val HtmlTag = Regex("<[^>]+>")

private fun CommonsPageDto.toPhoto(): PlacePhoto? {
    val info = imageinfo.firstOrNull() ?: return null
    if (info.mime !in PhotoTypes) return null
    val url = info.thumburl?.substringBefore('?') ?: return null
    val artist = info.extmetadata["Artist"]?.value?.replace(HtmlTag, "")?.trim()?.takeIf { it.isNotEmpty() }
    val licence = info.extmetadata["LicenseShortName"]?.value?.trim()?.takeIf { it.isNotEmpty() }
    return PlacePhoto(url, info.descriptionurl, listOfNotNull(artist, licence).joinToString(" · ").ifEmpty { null })
}

/** The Commons `File:` title from an OSM `image` or `wikimedia_commons` value, or null when it points elsewhere. */
internal fun commonsFileTitle(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.startsWith("File:")) return trimmed
    val commonsPage = trimmed.substringAfter("commons.wikimedia.org/wiki/", "")
    if (commonsPage.startsWith("File:")) return commonsPage.substringBefore('?').decodeURLPart().replace('_', ' ')
    val upload = trimmed.substringAfter("upload.wikimedia.org/wikipedia/commons/", "")
    if (upload.isNotEmpty()) {
        val name = if (upload.startsWith("thumb/")) upload.split('/').getOrNull(3) else upload.substringAfterLast('/')
        return name?.takeIf { it.isNotEmpty() }?.let { "File:" + it.decodeURLPart().replace('_', ' ') }
    }
    return null
}
