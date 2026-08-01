package com.raindepartment.weather

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

internal interface WeatherLocationProvider {
    suspend fun currentOrNull(): WeatherLocation?
}

internal class AndroidWeatherLocationProvider(
    private val context: Context,
) : WeatherLocationProvider {
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override suspend fun currentOrNull(): WeatherLocation? {
        if (!context.hasCoarseLocationPermission()) return null
        val location = currentPlatformLocation() ?: return null
        return WeatherLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            label = reverseGeocode(location) ?: "Current location",
        )
    }

    private suspend fun currentPlatformLocation(): Location? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            currentLocationApi30() ?: lastKnownLocation()
        } else {
            lastKnownLocation()
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private suspend fun currentLocationApi30(): Location? {
        val provider = sequenceOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).firstOrNull { providerName ->
            runCatching { locationManager.isProviderEnabled(providerName) }.getOrDefault(false)
        } ?: return null

        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: RuntimeException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnownLocation(): Location? = sequenceOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, context.resources.configuration.locales[0])
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                    val region = address.adminArea
                    when {
                        city.isNullOrBlank() -> region?.takeIf(String::isNotBlank)
                        region.isNullOrBlank() || city == region -> city
                        else -> "$city, $region"
                    }
                }
        }.getOrNull()
    }
}

internal fun Context.hasCoarseLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
