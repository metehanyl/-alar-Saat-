package com.metehanyl.calarsaat.prayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Diyanet İşleri Başkanlığı'nın namazvakitleri.diyanet.gov.tr verisini ayna olarak sunan
 * topluluk servisi (ezanvakti.emushaf.net) üzerinden il/ilçe ve vakit bilgisi çeker.
 */
object DiyanetApi {

    private const val BASE_URL = "https://ezanvakti.emushaf.net"
    private const val TURKEY_COUNTRY_ID = "2"

    suspend fun getCities(): List<CityOption> = withContext(Dispatchers.IO) {
        val json = httpGet("$BASE_URL/sehirler/$TURKEY_COUNTRY_ID")
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(CityOption(id = obj.getString("SehirID"), name = obj.getString("SehirAdi")))
            }
        }
    }

    suspend fun getDistricts(cityId: String): List<DistrictOption> = withContext(Dispatchers.IO) {
        val json = httpGet("$BASE_URL/ilceler/${encodePathSegment(cityId)}")
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(DistrictOption(id = obj.getString("IlceID"), name = obj.getString("IlceAdi")))
            }
        }
    }

    suspend fun getPrayerTimes(districtId: String): List<PrayerDay> = withContext(Dispatchers.IO) {
        val json = httpGet("$BASE_URL/vakitler/${encodePathSegment(districtId)}")
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    PrayerDay(
                        tarih = obj.getString("MiladiTarihKisa"),
                        imsak = obj.getString("Imsak"),
                        gunes = obj.getString("Gunes"),
                        ogle = obj.getString("Ogle"),
                        ikindi = obj.getString("Ikindi"),
                        aksam = obj.getString("Aksam"),
                        yatsi = obj.getString("Yatsi")
                    )
                )
            }
        }
    }

    /** URL path parçalarına enjekte edilebilecek karakterleri temizler (savunma amaçlı). */
    private fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8")

    private fun httpGet(urlString: String): String {
        require(urlString.startsWith("https://")) { "Sadece HTTPS istekleri desteklenir: $urlString" }

        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        // Sunucu yönlendirme yaparsa (örn. http'ye düşürme), kendimiz takip etmiyoruz;
        // güvenilmeyen bir hedefe sessizce yönlenmeyi engeller.
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code: $urlString")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
