package com.metehanyl.calarsaat.prayer

import android.content.Context

/**
 * GPS/ağ konumundan otomatik olarak il/ilçe çözümleyip Diyanet vakitlerini çeker ve
 * diske kaydeder. Manuel şehir seçimi yok; konum izni verildiği sürece tamamen otomatik çalışır.
 */
class PrayerRepository(private val context: Context) {

    private val cache = PrayerCache(context)
    private val locationProvider = DeviceLocationProvider(context)

    fun getCachedBundle(): PrayerBundle? = cache.load()

    fun hasLocationPermission(): Boolean = locationProvider.hasLocationPermission()

    suspend fun refresh(): RefreshResult {
        val location = when (val lookup = resolveLocation()) {
            is LocationLookup.Success -> lookup.location
            is LocationLookup.Failure ->
                return RefreshResult.Failure("Konum belirlenemedi (${lookup.reason}). Lütfen konum iznini verin ve GPS'i açık tutun.")
        }

        return try {
            val days = DiyanetApi.getPrayerTimes(location.ilceId)
            if (days.isEmpty()) {
                RefreshResult.Failure("Diyanet sunucusundan vakit verisi alınamadı.")
            } else {
                val bundle = PrayerBundle(
                    sehirId = location.sehirId,
                    sehirAdi = location.sehirAdi,
                    ilceId = location.ilceId,
                    ilceAdi = location.ilceAdi,
                    days = days,
                    fetchedAtEpochMillis = System.currentTimeMillis()
                )
                cache.save(bundle)
                RefreshResult.Success(bundle)
            }
        } catch (e: Exception) {
            RefreshResult.Failure("İnternete bağlanılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.")
        }
    }

    private suspend fun resolveLocation(): LocationLookup {
        if (!locationProvider.hasLocationPermission()) {
            return LocationLookup.Failure("konum izni verilmedi")
        }
        val location = locationProvider.getCurrentLocation()
            ?: return LocationLookup.Failure("GPS/ağ konumu alınamadı")

        val area = reverseGeocode(context, location.latitude, location.longitude)
        val il = area.il
            ?: return LocationLookup.Failure("konum coğrafi olarak çözümlenemedi, il bulunamadı")

        return try {
            resolveCityAndDistrict(il, area.ilceCandidates)
        } catch (e: Exception) {
            LocationLookup.Failure("Diyanet şehir/ilçe listesi alınamadı: ${e.message}")
        }
    }

    private suspend fun resolveCityAndDistrict(il: String, ilceCandidates: List<String>): LocationLookup {
        val ilKey = trKey(il)
        val cities = DiyanetApi.getCities()
        val matchingCities = cities.filter { trKey(it.name) == ilKey }
            .ifEmpty { cities.filter { trKey(it.name).contains(ilKey) || ilKey.contains(trKey(it.name)) } }

        if (matchingCities.isEmpty()) return LocationLookup.Failure("şehir bulunamadı: $il")

        val candidateKeys = ilceCandidates.map { trKey(it) }.filter { it.isNotBlank() }

        // Aynı il adıyla eşleşen birden fazla şehir kaydı olabilir (mirror API'de yinelenen
        // kayıtlar görülüyor); sadece ilk kaydı kullanmak yerine, hedef ilçeyi içeren kaydı
        // bulana kadar hepsini dene.
        var lastFailure: LocationLookup.Failure? = null
        for (city in matchingCities) {
            val districts = try {
                DiyanetApi.getDistricts(city.id)
            } catch (e: Exception) {
                lastFailure = LocationLookup.Failure("${city.name} (id=${city.id}) için ilçe listesi alınamadı: ${e.message}")
                continue
            }

            if (districts.isEmpty()) {
                lastFailure = LocationLookup.Failure("${city.name} (id=${city.id}) için ilçe listesi boş döndü")
                continue
            }

            // İstanbul gibi büyükşehirlerde Diyanet'in ilçe listesi sadece uzak/banliyö ilçeleri
            // ayrı kayıt olarak tutar; merkez ilçeler şehrin kendi adıyla aynı olan "merkez" ilçe
            // kaydı altında toplanır. Aday hiçbir özel ilçeyle eşleşmezse bu merkez kaydına düş.
            val district = candidateKeys.firstNotNullOfOrNull { key ->
                districts.firstOrNull { trKey(it.name) == key }
            } ?: candidateKeys.firstNotNullOfOrNull { key ->
                districts.firstOrNull { trKey(it.name).contains(key) || key.contains(trKey(it.name)) }
            } ?: districts.firstOrNull { trKey(it.name) == trKey(city.name) }

            if (district != null) {
                return LocationLookup.Success(
                    SelectedLocation(
                        sehirId = city.id,
                        sehirAdi = city.name,
                        ilceId = district.id,
                        ilceAdi = district.name
                    )
                )
            }

            val candidatesText = if (ilceCandidates.isEmpty()) "aday yok" else ilceCandidates.joinToString("/")
            val officialNames = districts.joinToString(", ") { it.name }
            lastFailure = LocationLookup.Failure(
                "${city.name} (id=${city.id}) ilinde ilçe eşleşmedi, adaylar: $candidatesText. Resmi liste (${districts.size} ilçe): $officialNames"
            )
        }

        val cityRecordsText = matchingCities.joinToString(", ") { "${it.name}(id=${it.id})" }
        return lastFailure ?: LocationLookup.Failure("hiçbir şehir kaydında ilçe bulunamadı: $cityRecordsText")
    }

    private sealed class LocationLookup {
        data class Success(val location: SelectedLocation) : LocationLookup()
        data class Failure(val reason: String) : LocationLookup()
    }
}
