package nz.eloque.openmaps.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt

/** A dark variant of the MapLibre style [light], keeping all its layers and recolouring their paint. */
internal fun darkStyle(light: String): String {
    val style = Json.parseToJsonElement(light).jsonObject
    val layers = style.getValue("layers").jsonArray.map { darken(it.jsonObject) }
    return JsonObject(style + ("layers" to JsonArray(layers))).toString()
}

private enum class Tone(
    val lightness: (Double) -> Double,
    val grey: Boolean = false,
) {
    INVERTED({ 0.19 + (1 - it) * 0.62 }),
    WATER({ 0.1 + (1 - it) * 0.2 }),
    BUILDING({ 0.21 + (1 - it) * 0.2 }, grey = true),
    MINOR_ROAD({ 0.08 + it * 0.24 }, grey = true),
    MAJOR_ROAD({ 0.3 + it * 0.15 }, grey = true),
    CASING({ 0.16 }, grey = true),
}

private val RoadLayer = Regex("^(road|bridge|tunnel)_")
private val MajorRoad = Regex("motorway|trunk|primary|secondary|tertiary|link")

private fun darken(layer: JsonObject): JsonObject {
    val id = layer["id"]?.jsonPrimitive?.content.orEmpty()
    val type = layer["type"]?.jsonPrimitive?.content
    val tone =
        when {
            id.startsWith("building") -> Tone.BUILDING
            id.startsWith("water") && type != "symbol" -> Tone.WATER
            type != "line" || !RoadLayer.containsMatchIn(id) || "rail" in id -> Tone.INVERTED
            "casing" in id -> Tone.CASING
            MajorRoad.containsMatchIn(id) -> Tone.MAJOR_ROAD
            else -> Tone.MINOR_ROAD
        }
    val paint =
        layer["paint"]?.jsonObject.orEmpty().mapValues { (key, value) ->
            if (key.endsWith("color")) recolor(value, tone) else value
        }
    val extra =
        when {
            type == "raster" -> mapOf("raster-brightness-max" to JsonPrimitive(RASTER_BRIGHTNESS))
            paint.containsKey("fill-pattern") -> mapOf("fill-opacity" to JsonPrimitive(PATTERN_OPACITY))
            else -> emptyMap()
        }
    val changes =
        buildMap {
            put("paint", JsonObject(paint + extra))
            if (id.startsWith("building")) put("minzoom", JsonPrimitive(BUILDING_MIN_ZOOM))
            if (id == "landuse_residential") put("layout", JsonObject(mapOf("visibility" to JsonPrimitive("none"))))
        }
    return JsonObject(layer + changes)
}

private fun recolor(
    value: JsonElement,
    tone: Tone,
): JsonElement =
    when (value) {
        is JsonArray -> JsonArray(value.map { recolor(it, tone) })
        is JsonPrimitive -> if (value.isString) parseColor(value.content)?.let { JsonPrimitive(it.toned(tone)) } ?: value else value
        else -> value
    }

internal data class Hsla(
    val h: Double,
    val s: Double,
    val l: Double,
    val a: Double,
)

/** Greys and roads turn a near-neutral grey, greens deep teal, and other colours a muted violet. */
private fun Hsla.toned(tone: Tone): String {
    val chroma = s * (1 - abs(2 * l - 1))
    val dark = tone.lightness(l).coerceIn(0.0, 1.0)
    val (h, s, l) =
        when {
            tone.grey || chroma < NEUTRAL_CHROMA -> Triple(GREY_HUE, GREY_SATURATION, dark)
            h in 60.0..170.0 -> Triple(TEAL_HUE, 0.45, minOf(dark, GREEN_LIGHTNESS))
            else -> Triple(h + (VIOLET_HUE - h) * 0.6, minOf(s * 0.3, 0.2), dark)
        }
    return "hsla(${h.roundToInt()},${percent(s)}%,${percent(l)}%,$a)"
}

private fun percent(value: Double) = (value * 1000).roundToInt() / 10.0

private val Functional = Regex("""^(rgba?|hsla?)\(([^)]*)\)$""")

/** Parses the CSS colour forms used in MapLibre styles, or returns null for any other string. */
internal fun parseColor(text: String): Hsla? {
    val value = text.trim().lowercase()
    if (value.startsWith("#")) {
        val hex = value.drop(1)
        val digits =
            when (hex.length) {
                3, 4 -> hex.map { "$it$it" }
                6, 8 -> hex.chunked(2)
                else -> return null
            }.map { it.toIntOrNull(16) ?: return null }
        return rgbToHsl(digits[0] / 255.0, digits[1] / 255.0, digits[2] / 255.0, digits.getOrNull(3)?.div(255.0) ?: 1.0)
    }
    val match = Functional.matchEntire(value) ?: return null
    val parts = match.groupValues[2].split(',').map { it.trim().removeSuffix("%").toDoubleOrNull() ?: return null }
    if (parts.size < 3) return null
    val alpha = parts.getOrNull(3) ?: 1.0
    return if (match.groupValues[1].startsWith("rgb")) {
        rgbToHsl(parts[0] / 255.0, parts[1] / 255.0, parts[2] / 255.0, alpha)
    } else {
        Hsla(parts[0], parts[1] / 100, parts[2] / 100, alpha)
    }
}

private fun rgbToHsl(
    r: Double,
    g: Double,
    b: Double,
    a: Double,
): Hsla {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2
    val d = max - min
    if (d == 0.0) return Hsla(0.0, 0.0, l, a)
    val s = d / (1 - abs(2 * l - 1))
    val h =
        when (max) {
            r -> 60 * (((g - b) / d).mod(6.0))
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
    return Hsla(h, s, l, a)
}

private const val RASTER_BRIGHTNESS = 0.3
private const val PATTERN_OPACITY = 0.15
private const val BUILDING_MIN_ZOOM = 16.5
private const val NEUTRAL_CHROMA = 0.1
private const val GREY_HUE = 220.0
private const val GREY_SATURATION = 0.06
private const val TEAL_HUE = 192.0
private const val GREEN_LIGHTNESS = 0.22
private const val VIOLET_HUE = 240.0
