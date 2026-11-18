package nz.eloque.openmaps.navigation

import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.ManeuverType
import nz.eloque.openmaps.core.model.TransportMode

internal val ManeuverType.icon: Int
    get() =
        when (this) {
            ManeuverType.DEPART -> R.drawable.car_navigation
            ManeuverType.ARRIVE -> R.drawable.car_flag
            ManeuverType.STRAIGHT, ManeuverType.OTHER -> R.drawable.car_straight
            ManeuverType.SLIGHT_LEFT -> R.drawable.car_turn_slight_left
            ManeuverType.LEFT -> R.drawable.car_turn_left
            ManeuverType.SHARP_LEFT -> R.drawable.car_turn_sharp_left
            ManeuverType.SLIGHT_RIGHT -> R.drawable.car_turn_slight_right
            ManeuverType.RIGHT -> R.drawable.car_turn_right
            ManeuverType.SHARP_RIGHT -> R.drawable.car_turn_sharp_right
            ManeuverType.U_TURN -> R.drawable.car_u_turn_left
            ManeuverType.ROUNDABOUT -> R.drawable.car_roundabout_right
        }

internal val TransportMode.icon: Int
    get() =
        when (this) {
            TransportMode.WALK -> R.drawable.mode_walk
            TransportMode.BIKE, TransportMode.SCOOTER -> R.drawable.mode_bike
            TransportMode.CAR -> R.drawable.mode_car
            TransportMode.BUS, TransportMode.COACH -> R.drawable.mode_bus
            TransportMode.TRAM, TransportMode.CABLE_CAR, TransportMode.FUNICULAR -> R.drawable.mode_tram
            TransportMode.SUBWAY -> R.drawable.mode_subway
            TransportMode.FERRY -> R.drawable.mode_ferry
            TransportMode.SUBURBAN,
            TransportMode.REGIONAL_RAIL,
            TransportMode.LONG_DISTANCE_RAIL,
            TransportMode.HIGHSPEED_RAIL,
            TransportMode.NIGHT_RAIL,
            TransportMode.AIRPLANE,
            TransportMode.OTHER,
            -> R.drawable.mode_train
        }
