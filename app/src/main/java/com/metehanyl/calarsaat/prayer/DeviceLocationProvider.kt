package com.metehanyl.calarsaat.prayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Google Play Services gerektirmeden, platformun kendi LocationManager'ı ile tek seferlik
 * konum okuması yapar. Böylece Play Services'i olmayan cihazlarda da çalışır.
 */
class DeviceLocationProvider(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMillis: Long = 15_000L): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val cached = bestLastKnownLocation(locationManager)
        val cachedIsGoodEnough = cached != null &&
            System.currentTimeMillis() - cached.time < TimeUnit.MINUTES.toMillis(5) &&
            cached.accuracy <= 100f
        if (cachedIsGoodEnough) {
            return cached
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return cached
        }

        val fresh = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
                try {
                    locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        return fresh ?: cached
    }

    private fun bestLastKnownLocation(locationManager: LocationManager): Location? {
        var best: Location? = null
        for (provider in locationManager.getProviders(true)) {
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } ?: continue
            if (isBetterLocation(location, best)) {
                best = location
            }
        }
        return best
    }

    /**
     * Konum doğruluğunu da hesaba katar; sadece en yeni değil, en güvenilir konumu seçer.
     * Aksi halde GPS'ten gelen daha eski ama çok daha doğru bir konum, hücre/ağ tabanlı
     * ve kilometrelerce hata payı olabilen daha yeni bir konum tarafından yanlışlıkla geçilebilir.
     */
    private fun isBetterLocation(location: Location, current: Location?): Boolean {
        if (current == null) return true

        val timeDelta = location.time - current.time
        val isSignificantlyNewer = timeDelta > TimeUnit.MINUTES.toMillis(2)
        val isSignificantlyOlder = timeDelta < -TimeUnit.MINUTES.toMillis(2)
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val accuracyDelta = (location.accuracy - current.accuracy)
        val isLessAccurate = accuracyDelta > 0
        val isSignificantlyLessAccurate = accuracyDelta > 200f
        val isFromSameProvider = location.provider == current.provider

        return when {
            !isLessAccurate -> true
            isNewer && !isSignificantlyLessAccurate && isFromSameProvider -> true
            else -> false
        }
    }
}
