package nz.eloque.openmaps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LocationFix
import nz.eloque.openmaps.core.model.LocationUpdates

/** Satellite fixes from the platform location service, without Google Play Services. */
class AndroidLocationUpdates(
    private val context: Context,
) : LocationUpdates {
    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LocationFix> =
        callbackFlow {
            check(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "Precise location permission missing"
            }
            val manager = context.getSystemService(LocationManager::class.java)
            val listener = LocationListener { trySend(it.toFix()) }
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { trySend(it.toFix()) }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper())
            awaitClose { manager.removeUpdates(listener) }
        }
}

private fun Location.toFix() =
    LocationFix(
        location = LatLng(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        speed = if (hasSpeed()) speed else null,
        bearing = if (hasBearing()) bearing else null,
        timeMillis = time,
    )

private const val INTERVAL_MILLIS = 1_000L
