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

    @Suppress("DEPRECATION")
    suspend fun placeName(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val a = Geocoder(context).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@withContext null
            a.featureName?.takeIf { it.isNotBlank() && it != a.subThoroughfare }
                ?: a.thoroughfare ?: a.locality
        } catch (e: Exception) {
            null
        }
    }
}
