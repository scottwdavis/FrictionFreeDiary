package com.frictionfree.diary.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // 1. Check last known location across providers
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time || loc.accuracy < bestLocation.accuracy) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (_: SecurityException) {} catch (_: Exception) {}
        }

        // Return immediately if last known location is fresh (within last 15 minutes)
        val fifteenMinutesAgo = System.currentTimeMillis() - 15 * 60 * 1000
        if (bestLocation != null && bestLocation.time > fifteenMinutesAgo) {
            return@withContext bestLocation
        }

        // 2. Otherwise, request a fresh location fix (Android 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val freshLocation = suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        val provider = when {
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                            else -> LocationManager.PASSIVE_PROVIDER
                        }
                        locationManager.getCurrentLocation(
                            provider,
                            signal,
                            context.mainExecutor
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                if (freshLocation != null) return@withContext freshLocation
            } catch (_: Exception) {}
        }

        return@withContext bestLocation
    }

    suspend fun getPlaceName(context: Context, latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                return@withContext formatCoordinates(latitude, longitude)
            }

            val geocoder = Geocoder(context, Locale.getDefault())

            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<Address>) {
                                if (cont.isActive) cont.resume(results)
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                try {
                    geocoder.getFromLocation(latitude, longitude, 1)
                } catch (_: Exception) {
                    null
                }
            }

            val address = addresses?.firstOrNull()
            if (address != null) {
                val city = address.locality ?: address.subAdminArea ?: address.subLocality
                val stateOrCountry = address.adminArea ?: address.countryName
                return@withContext when {
                    !city.isNullOrBlank() && !stateOrCountry.isNullOrBlank() -> "$city, $stateOrCountry"
                    !city.isNullOrBlank() -> city
                    !stateOrCountry.isNullOrBlank() -> stateOrCountry
                    !address.featureName.isNullOrBlank() -> address.featureName
                    else -> formatCoordinates(latitude, longitude)
                }
            }
        } catch (_: Exception) {}

        return@withContext formatCoordinates(latitude, longitude)
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return "%.4f, %.4f".format(Locale.US, latitude, longitude)
    }
}