package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var bestLast: Location? = null
        val maxAgeMs = 600_000L
        val now = System.currentTimeMillis()
        for (provider in providers) {
            val loc = try {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (loc != null && now - loc.time <= maxAgeMs) {
                if (bestLast == null || loc.time > bestLast.time) {
                    bestLast = loc
                }
            }
        }
        if (bestLast != null) {
            return bestLast.latitude to bestLast.longitude
        }

        val activeProvider = providers.firstOrNull { provider ->
            try {
                provider != LocationManager.PASSIVE_PROVIDER && manager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        } ?: return null

        val fresh = withTimeoutOrNull(8000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        manager.getCurrentLocation(
                            activeProvider,
                            signal,
                            ContextCompat.getMainExecutor(context),
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        override fun onProviderDisabled(provider: String) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    try {
                        @Suppress("DEPRECATION")
                        manager.requestSingleUpdate(activeProvider, listener, Looper.getMainLooper())
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        return fresh?.let { it.latitude to it.longitude }
    }

    suspend fun resolveCityName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("ru"))
        return withTimeoutOrNull(4000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            val addr = addresses.firstOrNull()
                            val name = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                            if (cont.isActive) cont.resume(name?.takeIf { it.isNotBlank() })
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                        (addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea)
                            ?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }
}
