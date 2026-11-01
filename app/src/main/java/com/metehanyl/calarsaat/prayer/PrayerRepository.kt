package com.metehanyl.calarsaat.prayer

import android.content.Context

class PrayerRepository(private val context: Context) {

    private val cache = PrayerCache(context)
    private val locationProvider = DeviceLocationProvider(context)

    fun getCachedBundle(): PrayerBundle? = cache.load()

    fun hasLocationPermission(): Boolean = locationProvider.hasLocationPermission()

    suspend fun refresh(): RefreshResult {
        if (!locationProvider.hasLocationPermission()) {
            return RefreshResult.Failure("Konum izni verilmedi. Lütfen konum iznini verin.")
        }
        val loc = locationProvider.getCurrentLocation()
            ?: return RefreshResult.Failure("GPS/ağ konumu alınamadı. GPS'in açık olduğundan emin olun.")

        // Best-effort reverse geocode for display name; does not block if it fails
        val area = try { reverseGeocode(context, loc.latitude, loc.longitude) }
                   catch (_: Exception) { GeoArea(null, emptyList()) }
        val sehirAdi = area.il ?: "GPS Konumu"
        val ilceAdi  = area.ilceCandidates.firstOrNull() ?: ""

        return try {
            val days = DiyanetApi.getPrayerTimesFromCoords(loc.latitude, loc.longitude)
            if (days.isEmpty()) {
                RefreshResult.Failure("AlAdhan API'sinden vakit verisi alınamadı. Lütfen internet bağlantınızı kontrol edin.")
            } else {
                val bundle = PrayerBundle(
                    sehirId              = "",
                    sehirAdi             = sehirAdi,
                    ilceId               = "",
                    ilceAdi              = ilceAdi,
                    days                 = days,
                    fetchedAtEpochMillis = System.currentTimeMillis()
                )
                cache.save(bundle)
                RefreshResult.Success(bundle)
            }
        } catch (e: Exception) {
            RefreshResult.Failure(
                "İnternete bağlanılamadı veya vakit sunucusu yanıt vermedi. " +
                "Lütfen bağlantınızı kontrol edip tekrar deneyin."
            )
        }
    }
}
