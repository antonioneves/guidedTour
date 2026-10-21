package com.guidedtour.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class Locator(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Current location, or null if permission is missing or no fix is available. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Location? = try {
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: SecurityException) {
        null
    }

    /** What's at a spot: its name, and the country's code (e.g. "PT"), which helps tell close languages apart. */
    data class Spot(val name: String?, val countryCode: String?)

    @Suppress("DEPRECATION")
    suspend fun spot(lat: Double, lng: Double): Spot = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext Spot(null, null)
            val a = Geocoder(context).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@withContext Spot(null, null)
            Spot(a.featureName?.takeIf { it.isNotBlank() && it != a.subThoroughfare } ?: a.thoroughfare ?: a.locality, a.countryCode)
        } catch (e: Exception) {
            Spot(null, null)
        }
    }
}
