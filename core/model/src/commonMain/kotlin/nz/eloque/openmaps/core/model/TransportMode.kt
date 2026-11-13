package nz.eloque.openmaps.core.model

enum class TransportMode {
    WALK,
    BIKE,
    CAR,
    BUS,
    COACH,
    TRAM,
    SUBWAY,
    SUBURBAN,
    REGIONAL_RAIL,
    LONG_DISTANCE_RAIL,
    HIGHSPEED_RAIL,
    NIGHT_RAIL,
    FERRY,
    AIRPLANE,
    CABLE_CAR,
    FUNICULAR,
    OTHER,
    ;

    val isTransit: Boolean
        get() = this != WALK && this != BIKE && this != CAR
}
