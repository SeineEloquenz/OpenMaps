package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Trimmed from the German and English Wikivoyage articles on Karlsruhe.
private const val WIKITEXT =
    """
== Sehenswürdigkeiten ==
* {{vCard | name = St. Stephan | type = church | lat = 49.00861 | long = 8.39938 | wikidata=Q2323225 | description = (kath.): Der von
Friedrich Weinbrenner gebaute Rundbau ist dem Pantheon in [[Rom]] nachempfunden.<ref>Quelle</ref> {{Preis|5}}
}}
[[File:Karlsruhe asv2022-10 img04 StStephan.jpg|mini|Pfarrkirche St. Stephan]]
* {{vCard | name = Schloss Karlsruhe | type = castle | wikidata = Q517334 | description = Bildet das '''Zentrum''' der [[Stadt|Innenstadt]]. }}
* {{vCard | name = Ohne Text | lat = 49.0 | long = 8.4 }}
*{{eat
| name=Café Bleu | url= | email=
| address=Kaiserallee 11 | lat=49.0099578 | long= 8.3845052 | directions=
| content=Restaurant owned by the same purveyor that runs "Die Kippe".
}}
"""

class WikivoyageListingsTest {
    private val listings = wikivoyageListings(WIKITEXT)

    @Test
    fun listingsWithADescriptionAreRead() {
        assertEquals(listOf("St. Stephan", "Schloss Karlsruhe", "Café Bleu"), listings.map { it.name })
        assertEquals(
            "(kath.): Der von Friedrich Weinbrenner gebaute Rundbau ist dem Pantheon in Rom nachempfunden.",
            listings[0].description,
        )
        assertEquals("Bildet das Zentrum der Innenstadt.", listings[1].description)
        assertEquals(LatLng(49.0099578, 8.3845052), listings[2].location)
        assertEquals("Q517334", listings[1].wikidata)
    }

    @Test
    fun placesMatchByWikidataOrByNameNearby() {
        val (church, castle, cafe) = listings
        assertTrue(castle.matches("Karlsruher Schloss", LatLng(49.0135, 8.4044), "Q517334"))
        assertTrue(cafe.matches("Cafe Bleu", LatLng(49.0101, 8.3846), null).not())
        assertTrue(cafe.matches("café bleu", LatLng(49.0101, 8.3846), null))
        assertFalse(cafe.matches("Café Bleu", LatLng(48.0, 8.0), null))
        assertTrue(castle.matches("Schloss Karlsruhe", LatLng(49.0135, 8.4044), null))
        assertFalse(church.matches("St. Bernhard", LatLng(49.00861, 8.39938), "Q1"))
    }
}
