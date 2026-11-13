package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.Alert
import nz.eloque.openmaps.core.model.Departure
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.Waypoint
import kotlin.time.Instant

internal fun transportMode(motisMode: String): TransportMode =
    when (motisMode) {
        "WALK" -> TransportMode.WALK
        "BIKE", "RENTAL" -> TransportMode.BIKE
        "CAR", "CAR_PARKING", "CAR_DROPOFF", "ODM", "RIDE_SHARING" -> TransportMode.CAR
        "BUS" -> TransportMode.BUS
        "COACH" -> TransportMode.COACH
        "TRAM" -> TransportMode.TRAM
        "SUBWAY", "METRO" -> TransportMode.SUBWAY
        "SUBURBAN" -> TransportMode.SUBURBAN
        "RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL" -> TransportMode.REGIONAL_RAIL
        "LONG_DISTANCE" -> TransportMode.LONG_DISTANCE_RAIL
        "HIGHSPEED_RAIL" -> TransportMode.HIGHSPEED_RAIL
        "NIGHT_RAIL" -> TransportMode.NIGHT_RAIL
        "FERRY" -> TransportMode.FERRY
        "AIRPLANE" -> TransportMode.AIRPLANE
        "CABLE_CAR", "AERIAL_LIFT" -> TransportMode.CABLE_CAR
        "FUNICULAR" -> TransportMode.FUNICULAR
        else -> TransportMode.OTHER
    }

internal fun parseColor(hex: String?): Long? {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return digits.toLongOrNull(16)?.let { 0xFF000000 or it }
}

/** Alert texts combine translations with ` -|`. Only the first one is kept. */
private fun firstTranslation(text: String): String = text.substringBefore(" -|").trim()

internal fun AlertDto.toAlert(): Alert? {
    val header = firstTranslation(headerText).ifBlank { return null }
    val description = descriptionText?.let(::firstTranslation)?.takeIf { it.isNotBlank() && it != header }
    return Alert(header, description)
}

private fun eventTime(
    scheduled: String?,
    actual: String?,
    realTime: Boolean,
): EventTime? {
    val actualInstant = (actual ?: scheduled)?.let(Instant::parse) ?: return null
    val scheduledInstant = scheduled?.let(Instant::parse) ?: actualInstant
    return EventTime(scheduledInstant, actualInstant, realTime)
}

internal fun PlaceDto.toWaypoint(realTime: Boolean): Waypoint =
    Waypoint(
        name = name,
        location = LatLng(lat, lon),
        stopId = stopId,
        platform = track,
        scheduledPlatform = scheduledTrack,
        arrival = eventTime(scheduledArrival, arrival, realTime),
        departure = eventTime(scheduledDeparture, departure, realTime),
        isCancelled = cancelled,
        timeZone = tz,
    )

private fun line(
    mode: String,
    shortName: String?,
    displayName: String?,
    longName: String?,
    color: String?,
    textColor: String?,
    agency: String?,
) = Line(
    shortName = shortName?.takeIf { it.isNotBlank() } ?: displayName?.takeIf { it.isNotBlank() },
    longName = longName?.takeIf { it.isNotBlank() },
    mode = transportMode(mode),
    color = parseColor(color),
    textColor = parseColor(textColor),
    agency = agency,
)

internal fun LegDto.toLeg(): Leg {
    val start = eventTime(scheduledStartTime, startTime, realTime)!!
    val end = eventTime(scheduledEndTime, endTime, realTime)!!
    val geometry = decodePolyline(legGeometry.points, legGeometry.precision.toInt())
    val mode = transportMode(mode)
    if (!mode.isTransit) {
        return Leg.Street(mode, from.toWaypoint(realTime), to.toWaypoint(realTime), start, end, geometry, distance)
    }
    return Leg.Transit(
        from = from.toWaypoint(realTime),
        to = to.toWaypoint(realTime),
        start = start,
        end = end,
        geometry = geometry,
        line = line(this.mode, routeShortName, displayName, routeLongName, routeColor, routeTextColor, agencyName),
        headsign = headsign,
        tripId = tripId,
        intermediateStops = intermediateStops.map { it.toWaypoint(realTime) },
        alerts = alerts.mapNotNull { it.toAlert() },
        isCancelled = cancelled,
        isWheelchairAccessible =
            when (wheelchairAccessible) {
                "ACCESSIBLE" -> true
                "NOT_ACCESSIBLE" -> false
                else -> null
            },
    )
}

internal fun ItineraryDto.toItinerary(): Itinerary =
    Itinerary(
        id = id.ifBlank { "$startTime/$endTime/${legs.joinToString(",") { it.mode + it.from.name }}" },
        start = Instant.parse(startTime),
        end = Instant.parse(endTime),
        transfers = transfers.toInt(),
        legs = legs.map { it.toLeg() },
    )

internal fun StopTimeDto.toDeparture(): Departure? {
    val time = eventTime(place.scheduledDeparture, place.departure, realTime) ?: return null
    return Departure(
        stopId = place.stopId,
        stopName = place.name,
        stopLocation = LatLng(place.lat, place.lon),
        line = line(mode, routeShortName, displayName, routeLongName, routeColor, routeTextColor, agencyName),
        headsign = headsign,
        time = time,
        platform = place.track,
        tripId = tripId,
        isCancelled = cancelled || tripCancelled,
        alerts = place.alerts.mapNotNull { it.toAlert() },
    )
}

internal fun MatchDto.toSearchResult(): SearchResult {
    val location = LatLng(lat, lon)
    val area = areas.firstOrNull { it.isDefault }?.name?.takeIf { it != name }
    return when (type) {
        "STOP" -> SearchResult.Stop(id, name, location, area, modes.map(::transportMode).toSet())
        "ADDRESS" -> SearchResult.Address(name, location, area, street, houseNumber, zip)
        else -> SearchResult.Place(OsmId.parse(id), name, location, area, category)
    }
}
