package com.metehanyl.calarsaat.prayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * api.aladhan.com üzerinden koordinat bazlı namaz vakitlerini çeker.
 * Kimlik doğrulama gerektirmez; method=13 Diyanet hesaplama yöntemini kullanır.
 */
object DiyanetApi {

    /** Uyumluluk için bırakıldı — artık ilce listesi yüklenmiyor. */
    fun init(context: Context) = Unit

    fun invalidateToken() = Unit

    /** GPS koordinatlarından o ayın namaz vakitlerini çeker. */
    suspend fun getPrayerTimesFromCoords(latitude: Double, longitude: Double): List<PrayerDay> =
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val url = "https://api.aladhan.com/v1/calendar/$year/$month" +
                "?latitude=$latitude&longitude=$longitude&method=13"
            val json = httpGet(url)
            parseAladhanResponse(json)
        }

    private fun parseAladhanResponse(json: String): List<PrayerDay> {
        val root = JSONObject(json)
        if (root.optInt("code", 0) != 200) return emptyList()
        val data = root.getJSONArray("data")
        val result = mutableListOf<PrayerDay>()
        for (i in 0 until data.length()) {
            val dayObj = data.getJSONObject(i)
            val timings = dayObj.getJSONObject("timings")
            // aladhan returns "DD-MM-YYYY"; convert to "dd.MM.yyyy"
            val rawDate = dayObj.getJSONObject("date").getJSONObject("gregorian").getString("date")
            val tarih = rawDate.replace("-", ".")
            result.add(
                PrayerDay(
                    tarih   = tarih,
                    imsak   = cleanTime(timings.optString("Imsak")),
                    gunes   = cleanTime(timings.optString("Sunrise")),
                    ogle    = cleanTime(timings.optString("Dhuhr")),
                    ikindi  = cleanTime(timings.optString("Asr")),
                    aksam   = cleanTime(timings.optString("Maghrib")),
                    yatsi   = cleanTime(timings.optString("Isha"))
                )
            )
        }
        return result
    }

    /** Strips timezone suffix, e.g. "05:10 (+03)" → "05:10". */
    private fun cleanTime(raw: String): String = raw.substringBefore(" ").trim()

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "CalarSaat-Android/1.0")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: $urlString")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
