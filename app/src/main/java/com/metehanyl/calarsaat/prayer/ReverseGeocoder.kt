package com.metehanyl.calarsaat.prayer

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GeoArea(val il: String?, val ilceCandidates: List<String>)

/**
 * Enlem/boylamı il ve olası ilçe adı adaylarına çevirir. Cihazın sistem Geocoder'ı ve ağ üzerinden
 * OpenStreetMap Nominatim her zaman birlikte çalıştırılıp adayları birleştirilir; çünkü geocoder'lar
 * "ilçe" kavramını farklı alanlarda (bazen mahalle, bazen belde adıyla) tutarsız şekilde döndürebilir.
 * Birden fazla aday toplamak, tek bir alanın yanlış/eksik olması durumunda doğru ilçeyi bulma şansını
 * artırır. İl bilgisi hiçbir kaynaktan gelmezse null döner.
 */
suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): GeoArea =
    withContext(Dispatchers.IO) {
        val deviceResult = deviceGeocode(context, latitude, longitude)
        val networkResult = networkGeocode(latitude, longitude)
        val il = deviceResult?.il ?: networkResult.il
        val candidates = (deviceResult?.ilceCandidates ?: emptyList()) + networkResult.ilceCandidates
        GeoArea(il = il, ilceCandidates = candidates.distinct())
    }

private fun deviceGeocode(context: Context, latitude: Double, longitude: Double): GeoArea? =
    try {
        if (!Geocoder.isPresent()) null
        else {
            val geocoder = Geocoder(context, Locale("tr", "TR"))
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(latitude, longitude, 1)
            val address = results?.firstOrNull()
            if (address == null) null
            else GeoArea(
                il = address.adminArea,
                ilceCandidates = listOfNotNull(
                    address.subAdminArea,
                    address.locality,
                    address.subLocality
                ).filter { it.isNotBlank() }
            )
        }
    } catch (e: Exception) {
        null
    }

private fun networkGeocode(latitude: Double, longitude: Double): GeoArea =
    try {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
            "&lat=$latitude&lon=$longitude&zoom=14&addressdetails=1&accept-language=tr"
        require(url.startsWith("https://")) { "Sadece HTTPS istekleri desteklenir: $url" }

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "CalarSaat-Android/1.0 (Turkce alarm uygulamasi)")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: $url")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val address = JSONObject(body).optJSONObject("address")
            if (address == null) GeoArea(null, emptyList())
            else GeoArea(
                il = address.optString("state").ifBlank { null },
                // Türkiye'de ilçe bilgisi OSM'de tutarsız alanlarda gelebildiğinden, tek bir alan
                // seçmek yerine olası tüm adaylar toplanır; eşleştirme aşamasında hepsi denenir.
                ilceCandidates = listOfNotNull(
                    address.optString("city_district").ifBlank { null },
                    address.optString("district").ifBlank { null },
                    address.optString("county").ifBlank { null },
                    address.optString("town").ifBlank { null },
                    address.optString("suburb").ifBlank { null },
                    address.optString("neighbourhood").ifBlank { null }
                )
            )
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        GeoArea(null, emptyList())
    }
