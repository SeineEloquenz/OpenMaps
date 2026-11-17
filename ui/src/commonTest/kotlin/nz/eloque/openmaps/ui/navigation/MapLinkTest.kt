package nz.eloque.openmaps.ui.navigation

import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.TravelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapLinkTest {
    private val karlsruhe = LatLng(49.0094, 8.4044)

    @Test
    fun geoCoordinates() {
        assertEquals(MapLink.Show(karlsruhe, null), parseMapLink("geo:49.0094,8.4044"))
        assertEquals(MapLink.Show(karlsruhe, null), parseMapLink("geo:49.0094,8.4044;u=35?z=15"))
    }

    @Test
    fun geoQueryWithLabel() {
        assertEquals(MapLink.Show(karlsruhe, "Hauptbahnhof"), parseMapLink("geo:0,0?q=49.0094,8.4044(Hauptbahnhof)"))
    }

    @Test
    fun geoAddressFromContacts() {
        assertEquals(MapLink.Find("Kaiserstraße 12, Karlsruhe"), parseMapLink("geo:0,0?q=Kaiserstra%C3%9Fe+12%2C+Karlsruhe"))
    }

    @Test
    fun emptyGeoIsIgnored() {
        assertNull(parseMapLink("geo:0,0"))
        assertNull(parseMapLink("mailto:someone@example.org"))
    }

    @Test
    fun navigationIntent() {
        assertEquals(MapLink.Route(karlsruhe, null, TravelMode.WALK), parseMapLink("google.navigation:q=49.0094,8.4044&mode=w"))
        assertEquals(MapLink.Route(null, "Heidelberg Hbf", null), parseMapLink("google.navigation:q=Heidelberg+Hbf"))
    }

    @Test
    fun googleLinks() {
        assertEquals(
            MapLink.Show(karlsruhe, "Schloss Karlsruhe"),
            parseMapLink("https://www.google.com/maps/place/Schloss+Karlsruhe/@49.0094,8.4044,17z/data=!3m1"),
        )
        assertEquals(MapLink.Show(karlsruhe, null), parseMapLink("https://maps.google.com/?q=49.0094,8.4044"))
        assertEquals(MapLink.Find("pizza"), parseMapLink("https://www.google.com/maps/search/?api=1&query=pizza"))
        assertEquals(
            MapLink.Route(null, "Heidelberg", TravelMode.BIKE),
            parseMapLink("https://www.google.com/maps/dir/?api=1&destination=Heidelberg&travelmode=bicycling"),
        )
        assertEquals(
            MapLink.Route(null, "Heidelberg", null),
            parseMapLink("https://www.google.de/maps/dir/Karlsruhe/Heidelberg/@49.2,8.5,10z"),
        )
    }

    @Test
    fun appleLinks() {
        assertEquals(MapLink.Show(karlsruhe, "Schloss"), parseMapLink("https://maps.apple.com/?ll=49.0094,8.4044&q=Schloss"))
        assertEquals(
            MapLink.Route(null, "Heidelberg", TravelMode.TRANSIT),
            parseMapLink("https://maps.apple.com/?daddr=Heidelberg&dirflg=r"),
        )
    }

    @Test
    fun openStreetMapLinks() {
        assertEquals(
            MapLink.Show(karlsruhe, null),
            parseMapLink("https://www.openstreetmap.org/?mlat=49.0094&mlon=8.4044#map=17/49.0094/8.4044"),
        )
        assertEquals(MapLink.Show(karlsruhe, null), parseMapLink("https://www.openstreetmap.org/#map=17/49.0094/8.4044"))
        assertEquals(MapLink.Show(karlsruhe, "Café"), parseMapLink("https://osmand.net/go?lat=49.0094&lon=8.4044&z=16&name=Caf%C3%A9"))
    }
}
