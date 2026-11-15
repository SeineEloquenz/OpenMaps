package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.BookingLinks
import nz.eloque.openmaps.core.model.VehicleKind
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.book_with
import nz.eloque.openmaps.ui.resources.vehicle_bike
import nz.eloque.openmaps.ui.resources.vehicle_car
import nz.eloque.openmaps.ui.resources.vehicle_cargo_bike
import nz.eloque.openmaps.ui.resources.vehicle_e_bike
import nz.eloque.openmaps.ui.resources.vehicle_moped
import nz.eloque.openmaps.ui.resources.vehicle_other
import nz.eloque.openmaps.ui.resources.vehicle_scooter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun VehicleKind.icon(electric: Boolean = false): ImageVector =
    when (this) {
        VehicleKind.BICYCLE -> if (electric) Icons.Default.ElectricBike else Icons.AutoMirrored.Filled.DirectionsBike
        VehicleKind.CARGO_BICYCLE -> Icons.Default.PedalBike
        VehicleKind.SCOOTER -> Icons.Default.ElectricScooter
        VehicleKind.MOPED -> Icons.Default.Moped
        VehicleKind.CAR -> Icons.Default.DirectionsCar
        VehicleKind.OTHER -> Icons.AutoMirrored.Filled.DirectionsBike
    }

fun VehicleKind.label(electric: Boolean = false): StringResource =
    when (this) {
        VehicleKind.BICYCLE -> if (electric) Res.string.vehicle_e_bike else Res.string.vehicle_bike
        VehicleKind.CARGO_BICYCLE -> Res.string.vehicle_cargo_bike
        VehicleKind.SCOOTER -> Res.string.vehicle_scooter
        VehicleKind.MOPED -> Res.string.vehicle_moped
        VehicleKind.CAR -> Res.string.vehicle_car
        VehicleKind.OTHER -> Res.string.vehicle_other
    }

/** Opens the operator's app, or its website when the app is not installed. */
fun UriHandler.openBooking(booking: BookingLinks) {
    val app = booking.app
    if (app != null) {
        try {
            openUri(app)
            return
        } catch (_: Exception) {
            // Without the operator's app the link has nowhere to go, so the website takes over.
        }
    }
    booking.web?.let(::openUri)
}

/** Nothing when [booking] has no link at all. */
@Composable
fun BookButton(
    operator: String,
    booking: BookingLinks,
    modifier: Modifier = Modifier,
) {
    if (booking.app == null && booking.web == null) return
    val uriHandler = LocalUriHandler.current
    FilledTonalButton(onClick = { uriHandler.openBooking(booking) }, modifier = modifier.height(36.dp)) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(stringResource(Res.string.book_with, operator), modifier = Modifier.padding(start = 6.dp))
    }
}
