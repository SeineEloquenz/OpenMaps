package nz.eloque.openmaps.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LIGHT =
    """{"version":8,"layers":[
    {"id":"background","type":"background","paint":{"background-color":"#f8f4f0"}},
    {"id":"road_minor_casing","type":"line","paint":{"line-color":"#cfcdca"}},
    {"id":"road_minor","type":"line","paint":{"line-color":"#fff"}},
    {"id":"road_motorway","type":"line","paint":{"line-color":["interpolate",["linear"],["zoom"],5,"hsl(26,87%,62%)",6,"#fc8"]}},
    {"id":"label_city","type":"symbol","paint":{"text-color":"#000","text-halo-color":"rgba(255,255,255,0.7)"}}]}"""

class DarkStyleTest {
    private val layers =
        Json.parseToJsonElement(darkStyle(LIGHT)).jsonObject.getValue("layers").jsonArray.associate {
            it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("paint").jsonObject
        }

    private fun lightness(
        layer: String,
        property: String,
    ) = parseColor(layers.getValue(layer).getValue(property).jsonPrimitive.content)!!.l

    @Test
    fun roadsStandOutFromTheDarkGround() {
        val ground = lightness("background", "background-color")
        assertTrue(ground < 0.25)
        assertTrue(lightness("road_minor", "line-color") > ground + 0.1)
        assertTrue(lightness("road_minor_casing", "line-color") < ground)
    }

    @Test
    fun labelsTurnLightOnDarkHalos() {
        assertTrue(lightness("label_city", "text-color") > 0.8)
        assertTrue(lightness("label_city", "text-halo-color") < 0.2)
        assertEquals(0.7, parseColor(layers.getValue("label_city").getValue("text-halo-color").jsonPrimitive.content)!!.a)
    }

    @Test
    fun recolorsInsideExpressions() {
        val stops = layers.getValue("road_motorway").getValue("line-color").jsonArray
        assertEquals("interpolate", stops[0].jsonPrimitive.content)
        assertTrue(parseColor(stops[6].jsonPrimitive.content)!!.l < 0.5)
    }

    @Test
    fun groundFollowsTheThemeAccent() {
        fun ground(tint: MapTint) =
            parseColor(
                Json.parseToJsonElement(darkStyle(LIGHT, tint)).jsonObject.getValue("layers").jsonArray[0]
                    .jsonObject.getValue("paint").jsonObject.getValue("background-color").jsonPrimitive.content,
            )!!

        val purple = ground(MapTint.of(0.8f, 0.7f, 1f))
        assertEquals(260.0, purple.h, 1.0)
        assertTrue(purple.s > 0.1)
        assertEquals(0.0, ground(MapTint.of(0.5f, 0.5f, 0.5f)).s)
    }

    @Test
    fun parsesStyleColors() {
        assertEquals(Hsla(0.0, 0.0, 1.0, 1.0), parseColor("#fff"))
        assertEquals(Hsla(35.0, 0.57, 0.88, 0.49), parseColor("hsla(35,57%,88%,0.49)"))
        assertEquals(0.5, parseColor("rgba(255, 0, 0, 0.5)")!!.a)
        assertNull(parseColor("linear"))
    }
}
