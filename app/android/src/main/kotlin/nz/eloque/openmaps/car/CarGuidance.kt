package nz.eloque.openmaps.car

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.Lane
import nz.eloque.openmaps.core.model.LaneDirection
import nz.eloque.openmaps.core.model.ManeuverType
import nz.eloque.openmaps.core.model.NavigationInstruction
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NavigationStatus
import java.util.TimeZone
import kotlin.time.Clock
import androidx.car.app.navigation.model.Lane as CarLane
import androidx.car.app.navigation.model.LaneDirection as CarLaneDirection

/** The car's view of a running trip: the next maneuvers and the arrival estimate. */
internal class CarGuidance(
    private val context: CarContext,
) {
    fun routingInfo(
        state: NavigationState,
        unit: DistanceUnit,
    ): RoutingInfo {
        val instruction = state.instruction
        if (state.status == NavigationStatus.REROUTING || instruction == null) {
            return RoutingInfo.Builder().setLoading(true).build()
        }
        return RoutingInfo.Builder()
            .setCurrentStep(step(instruction, withLanes = true), distance(state.metersToManeuver, unit))
            .apply { state.then?.let { setNextStep(step(it, withLanes = false)) } }
            .build()
    }

    fun estimate(
        state: NavigationState,
        unit: DistanceUnit,
    ): TravelEstimate {
        val arrival = Clock.System.now() + state.remainingTime
        return TravelEstimate.Builder(
            distance(state.remainingMeters, unit),
            DateTimeWithZone.create(arrival.toEpochMilliseconds(), TimeZone.getDefault()),
        ).setRemainingTimeSeconds(state.remainingTime.inWholeSeconds.coerceAtLeast(0)).build()
    }

    /** What the car shows in its instrument cluster and notifications while another screen is open. */
    fun trip(
        state: NavigationState,
        destination: String?,
        unit: DistanceUnit,
    ): Trip {
        val estimate = estimate(state, unit)
        val builder = Trip.Builder().setLoading(state.status == NavigationStatus.REROUTING)
        if (state.status != NavigationStatus.REROUTING) {
            state.instruction?.let { builder.addStep(step(it, withLanes = false), toManeuver(state, unit)) }
            destination?.takeIf { it.isNotBlank() }?.let {
                builder.addDestination(androidx.car.app.navigation.model.Destination.Builder().setName(it).build(), estimate)
            }
        }
        return builder.build()
    }

    /** The way to the next maneuver, its time taken as the same share of the remaining time as of the remaining distance. */
    private fun toManeuver(
        state: NavigationState,
        unit: DistanceUnit,
    ): TravelEstimate {
        val share = if (state.remainingMeters > 0) (state.metersToManeuver / state.remainingMeters).coerceIn(0.0, 1.0) else 0.0
        val seconds = (state.remainingTime.inWholeSeconds * share).toLong()
        val arrival = Clock.System.now().toEpochMilliseconds() + seconds * 1000
        return TravelEstimate.Builder(distance(state.metersToManeuver, unit), DateTimeWithZone.create(arrival, TimeZone.getDefault()))
            .setRemainingTimeSeconds(seconds)
            .build()
    }

    private fun step(
        instruction: NavigationInstruction,
        withLanes: Boolean,
    ): Step {
        val builder = Step.Builder(instruction.text).setManeuver(maneuver(instruction))
        instruction.secondary?.let { builder.setRoad(it) }
        if (withLanes && instruction.lanes.isNotEmpty()) {
            instruction.lanes.forEach { lane ->
                builder.addLane(
                    CarLane.Builder().apply {
                        lane.directions.ifEmpty { listOf(LaneDirection.STRAIGHT) }.forEach { direction ->
                            addDirection(CarLaneDirection.create(direction.shape, lane.recommended && lane.chosen == direction))
                        }
                    }.build(),
                )
            }
            builder.setLanesImage(lanesImage(instruction.lanes))
        }
        return builder.build()
    }

    private fun maneuver(instruction: NavigationInstruction): Maneuver {
        val (type, icon) =
            when (instruction.maneuver) {
                ManeuverType.DEPART -> Maneuver.TYPE_DEPART to R.drawable.car_navigation
                ManeuverType.ARRIVE -> Maneuver.TYPE_DESTINATION to R.drawable.car_flag
                ManeuverType.STRAIGHT, ManeuverType.OTHER -> Maneuver.TYPE_STRAIGHT to R.drawable.car_straight
                ManeuverType.SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT to R.drawable.car_turn_slight_left
                ManeuverType.LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT to R.drawable.car_turn_left
                ManeuverType.SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT to R.drawable.car_turn_sharp_left
                ManeuverType.SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT to R.drawable.car_turn_slight_right
                ManeuverType.RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT to R.drawable.car_turn_right
                ManeuverType.SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT to R.drawable.car_turn_sharp_right
                ManeuverType.U_TURN -> Maneuver.TYPE_U_TURN_LEFT to R.drawable.car_u_turn_left
                ManeuverType.ROUNDABOUT ->
                    (
                        if (instruction.exit != null) {
                            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
                        } else {
                            Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
                        }
                    ) to R.drawable.car_roundabout_right
            }
        val builder = Maneuver.Builder(type).setIcon(icon(icon))
        if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW) builder.setRoundaboutExitNumber(instruction.exit ?: 1)
        return builder.build()
    }

    /** All lanes side by side, the ones to take bright and the others dimmed, as the car shows lanes only as a picture. */
    private fun lanesImage(lanes: List<Lane>): CarIcon {
        val size = (LANE_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size * lanes.size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        lanes.forEachIndexed { index, lane ->
            val direction = lane.chosen ?: lane.directions.firstOrNull() ?: LaneDirection.STRAIGHT
            val drawable = ContextCompat.getDrawable(context, direction.icon)?.mutate() ?: return@forEachIndexed
            drawable.setTint(if (lane.recommended) LANE_ON else LANE_OFF)
            drawable.setBounds(index * size, 0, (index + 1) * size, size)
            drawable.draw(canvas)
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    fun icon(
        @DrawableRes resource: Int,
    ): CarIcon = CarIcon.Builder(IconCompat.createWithResource(context, resource)).build()
}

internal fun distance(
    meters: Double,
    unit: DistanceUnit,
): Distance =
    when (unit) {
        DistanceUnit.METRIC ->
            if (meters >= 1000) {
                Distance.create(meters / 1000, Distance.UNIT_KILOMETERS)
            } else {
                Distance.create(roundDown(meters, if (meters >= 100) 50.0 else 10.0), Distance.UNIT_METERS)
            }
        DistanceUnit.IMPERIAL -> {
            val miles = meters / METERS_PER_MILE
            if (miles >= 0.1) Distance.create(miles, Distance.UNIT_MILES) else Distance.create(meters * FEET_PER_METER, Distance.UNIT_FEET)
        }
    }

private fun roundDown(
    value: Double,
    step: Double,
) = (value / step).toInt() * step

private val LaneDirection.shape: Int
    get() =
        when (this) {
            LaneDirection.SHARP_LEFT -> CarLaneDirection.SHAPE_SHARP_LEFT
            LaneDirection.LEFT -> CarLaneDirection.SHAPE_NORMAL_LEFT
            LaneDirection.SLIGHT_LEFT -> CarLaneDirection.SHAPE_SLIGHT_LEFT
            LaneDirection.STRAIGHT -> CarLaneDirection.SHAPE_STRAIGHT
            LaneDirection.SLIGHT_RIGHT -> CarLaneDirection.SHAPE_SLIGHT_RIGHT
            LaneDirection.RIGHT -> CarLaneDirection.SHAPE_NORMAL_RIGHT
            LaneDirection.SHARP_RIGHT -> CarLaneDirection.SHAPE_SHARP_RIGHT
            LaneDirection.U_TURN -> CarLaneDirection.SHAPE_U_TURN_LEFT
        }

private val LaneDirection.icon: Int
    get() =
        when (this) {
            LaneDirection.SHARP_LEFT -> R.drawable.car_turn_sharp_left
            LaneDirection.LEFT -> R.drawable.car_turn_left
            LaneDirection.SLIGHT_LEFT -> R.drawable.car_turn_slight_left
            LaneDirection.STRAIGHT -> R.drawable.car_straight
            LaneDirection.SLIGHT_RIGHT -> R.drawable.car_turn_slight_right
            LaneDirection.RIGHT -> R.drawable.car_turn_right
            LaneDirection.SHARP_RIGHT -> R.drawable.car_turn_sharp_right
            LaneDirection.U_TURN -> R.drawable.car_u_turn_left
        }

private const val LANE_DP = 48
private const val LANE_ON = 0xFFFFFFFF.toInt()
private const val LANE_OFF = 0x66FFFFFF
private const val METERS_PER_MILE = 1609.344
private const val FEET_PER_METER = 3.28084
