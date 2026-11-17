package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.LatLng

/** A place written up in a Wikivoyage article through a listing template such as `{{vCard}}` or `{{eat}}`. */
internal data class WikivoyageListing(
    val name: String,
    val wikidata: String?,
    val location: LatLng?,
    val description: String,
)

/** Listings with a description in the [wikitext] of a Wikivoyage article. */
internal fun wikivoyageListings(wikitext: String): List<WikivoyageListing> {
    val listings = mutableListOf<WikivoyageListing>()
    var start = wikitext.indexOf("{{")
    while (start >= 0) {
        val end = templateEnd(wikitext, start)
        if (end < 0) break
        val body = wikitext.substring(start + 2, end)
        val name = body.substringBefore('|').trim().lowercase()
        if (name in ListingTemplates) {
            body.toListing()?.let(listings::add)
            start = wikitext.indexOf("{{", end)
        } else {
            start = wikitext.indexOf("{{", start + 2)
        }
    }
    return listings
}

/** Whether [listing] describes the place called [name] at [location] with the Wikidata item [wikidata]. */
internal fun WikivoyageListing.matches(
    name: String,
    location: LatLng,
    wikidata: String?,
): Boolean {
    if (wikidata != null && this.wikidata == wikidata) return true
    if (comparable(this.name) != comparable(name)) return false
    return this.location == null || this.location.distanceTo(location) <= SAME_PLACE_METERS
}

/** The index just past the `}}` closing the template opened at [start], or -1. */
private fun templateEnd(
    text: String,
    start: Int,
): Int {
    var depth = 0
    var i = start
    while (i < text.length - 1) {
        when {
            text.startsWith("{{", i) -> {
                depth++
                i += 2
            }
            text.startsWith("}}", i) -> {
                depth--
                i += 2
                if (depth == 0) return i - 2
            }
            else -> i++
        }
    }
    return -1
}

private fun String.toListing(): WikivoyageListing? {
    val params =
        topLevelParts(this).drop(1).mapNotNull { part ->
            val key = part.substringBefore('=', "").trim().lowercase()
            if (key.isEmpty()) null else key to part.substringAfter('=').trim()
        }.toMap()
    val name = params["name"]?.let(::plainText)?.takeIf { it.isNotEmpty() } ?: return null
    val description = (params["description"] ?: params["content"])?.let(::plainText)?.takeIf { it.isNotEmpty() } ?: return null
    val lat = params["lat"]?.toDoubleOrNull()
    val lon = (params["long"] ?: params["lon"])?.toDoubleOrNull()
    return WikivoyageListing(
        name = name,
        wikidata = params["wikidata"]?.takeIf { WikidataItem.matches(it) },
        location = if (lat != null && lon != null) LatLng(lat, lon) else null,
        description = description,
    )
}

/** Splits a template body at the `|` that are not inside a nested template or link. */
private fun topLevelParts(body: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var from = 0
    var i = 0
    while (i < body.length) {
        when {
            body.startsWith("{{", i) || body.startsWith("[[", i) -> {
                depth++
                i += 2
            }
            body.startsWith("}}", i) || body.startsWith("]]", i) -> {
                depth--
                i += 2
            }
            body[i] == '|' && depth == 0 -> {
                parts += body.substring(from, i)
                from = ++i
            }
            else -> i++
        }
    }
    parts += body.substring(from)
    return parts
}

internal fun plainText(wikitext: String): String {
    var text = wikitext.replace(Ref, "").replace(Comment, "")
    while (true) {
        val stripped = text.replace(InnerTemplate, "")
        if (stripped == text) break
        text = stripped
    }
    return text
        .replace(FileLink, "")
        .replace(PipedLink, "$1")
        .replace(PlainLink, "$1")
        .replace(ExternalLink, "$1")
        .replace(HtmlTag, "")
        .replace("'''", "")
        .replace("''", "")
        .replace(Whitespace, " ")
        .trim()
}

private fun comparable(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

private const val SAME_PLACE_METERS = 300.0

private val ListingTemplates = setOf("vcard", "listing", "see", "do", "eat", "drink", "sleep", "buy", "go")
private val WikidataItem = Regex("Q\\d+")
private val Ref = Regex("<ref[^>]*/>|<ref[^>]*>.*?</ref>", RegexOption.DOT_MATCHES_ALL)
private val Comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val InnerTemplate = Regex("\\{\\{[^{}]*\\}\\}")
private val FileLink = Regex("\\[\\[(?:File|Datei|Image|Bild|Fichier):[^\\]]*\\]\\]", RegexOption.IGNORE_CASE)
private val PipedLink = Regex("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]")
private val PlainLink = Regex("\\[\\[([^\\]]*)\\]\\]")
private val ExternalLink = Regex("\\[https?://\\S+\\s+([^\\]]*)\\]")
private val HtmlTag = Regex("<[^>]+>")
private val Whitespace = Regex("\\s+")
