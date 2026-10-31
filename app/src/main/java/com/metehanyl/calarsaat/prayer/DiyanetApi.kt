package com.metehanyl.calarsaat.prayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/**
 * Diyanet İşleri Başkanlığı'nın resmi namaz vakitleri API'si (awqatsalah.diyanet.gov.tr).
 * Kullanmak için uygulamayı kayıt ettirip e-posta ve şifreyi ayarlardan girin.
 * Kayıt: https://awqatsalah.diyanet.gov.tr
 */
object DiyanetApi {

    private const val BASE_URL = "https://awqatsalah.diyanet.gov.tr"

    /** Ayarlardan set edilir (SettingsActivity / PrayerRepository). */
    var email: String = ""
    var password: String = ""

    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedTurkeyId: Int = -1

    fun invalidateToken() {
        cachedToken = ""
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    private suspend fun getToken(): String {
        if (cachedToken.isNotBlank()) return cachedToken
        if (email.isBlank() || password.isBlank()) {
            throw IOException("Diyanet API kimlik bilgileri girilmemiş. Ayarlar > Diyanet API bölümünden e-posta ve şifreyi girin.")
        }
        val body = """{"email":${jsonStr(email)},"password":${jsonStr(password)}}"""
        val json = httpPost("$BASE_URL/Auth/Login", body, authToken = null)
        val obj = JSONObject(json)
        if (!obj.optBoolean("success", true)) {
            val msg = obj.optString("message", "Kimlik doğrulaması başarısız.")
            throw IOException("Diyanet giriş hatası: $msg")
        }
        val data = obj.optJSONObject("data")
            ?: throw IOException("Diyanet auth yanıtı geçersiz format.")
        val token = data.optString("accessToken", "")
        if (token.isBlank()) throw IOException("Diyanet'ten erişim token'ı alınamadı.")
        cachedToken = token
        return token
    }

    // ── Place ─────────────────────────────────────────────────────────────────

    private suspend fun getTurkeyId(token: String): Int {
        if (cachedTurkeyId != -1) return cachedTurkeyId
        return try {
            val json = httpGet("$BASE_URL/api/Place/Countries", token)
            val arr = parseDataArray(json)
            var found = -1
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = optName(obj)
                if (name.contains("türkiye", ignoreCase = true) ||
                    name.equals("turkey", ignoreCase = true)
                ) {
                    found = optId(obj)
                    if (found != -1) break
                }
            }
            if (found == -1) found = 2
            cachedTurkeyId = found
            found
        } catch (e: Exception) {
            2
        }
    }

    /**
     * Türkiye'deki iller listesi — [CityOption.id] = stateId (Places/States'den).
     */
    suspend fun getCities(): List<CityOption> = withContext(Dispatchers.IO) {
        val token = getToken()
        val turkeyId = getTurkeyId(token)
        val json = httpGet("$BASE_URL/api/Place/States/$turkeyId", token)
        val arr = parseDataArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = optId(obj)
                val name = optName(obj)
                if (id != -1 && name.isNotBlank()) add(CityOption(id.toString(), name))
            }
        }
    }

    /**
     * Bir ile ait ilçeler — [DistrictOption.id] = cityId (Places/Cities'den).
     */
    suspend fun getDistricts(cityId: String): List<DistrictOption> = withContext(Dispatchers.IO) {
        val token = getToken()
        val json = httpGet("$BASE_URL/api/Place/Cities/$cityId", token)
        val arr = parseDataArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = optId(obj)
                val name = optName(obj)
                if (id != -1 && name.isNotBlank()) add(DistrictOption(id.toString(), name))
            }
        }
    }

    /**
     * Bir ilçeye ait aylık namaz vakitleri.
     * Resmi API'de Fajr = İmsak, Sunrise = Güneş, Dhuhr = Öğle,
     * Asr = İkindi, Maghrib = Akşam, Isha = Yatsı.
     */
    suspend fun getPrayerTimes(districtId: String): List<PrayerDay> = withContext(Dispatchers.IO) {
        val token = getToken()
        val json = httpGet("$BASE_URL/api/PrayerTime/Monthly/$districtId", token)
        val arr = parseDataArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tarih = isoToTrDate(obj.optString("gregorianDateLongIso8601", ""))
                if (tarih.isBlank()) continue
                add(
                    PrayerDay(
                        tarih = tarih,
                        imsak   = obj.optString("Fajr",    obj.optString("fajr",    "")),
                        gunes   = obj.optString("Sunrise",  obj.optString("sunrise",  "")),
                        ogle    = obj.optString("Dhuhr",    obj.optString("dhuhr",    "")),
                        ikindi  = obj.optString("Asr",      obj.optString("asr",      "")),
                        aksam   = obj.optString("Maghrib",  obj.optString("maghrib",  "")),
                        yatsi   = obj.optString("Isha",     obj.optString("isha",     ""))
                    )
                )
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseDataArray(json: String): JSONArray {
        val obj = JSONObject(json)
        return obj.optJSONArray("data") ?: JSONArray()
    }

    private fun optId(obj: JSONObject): Int =
        if (obj.has("Id")) obj.optInt("Id", -1) else obj.optInt("id", -1)

    private fun optName(obj: JSONObject): String =
        if (obj.has("Name")) obj.optString("Name", "") else obj.optString("name", "")

    /** ISO-8601 tarihini "dd.MM.yyyy" formatına çevirir. */
    private fun isoToTrDate(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val dt = OffsetDateTime.parse(iso)
            "%02d.%02d.%d".format(dt.dayOfMonth, dt.monthValue, dt.year)
        } catch (e: Exception) {
            ""
        }
    }

    private fun jsonStr(s: String): String =
        "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun httpGet(urlString: String, token: String): String {
        require(urlString.startsWith("https://")) { "Sadece HTTPS: $urlString" }
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            val code = connection.responseCode
            if (code == 401) {
                invalidateToken()
                throw IOException("Diyanet API: yetkilendirme hatası (401). Token süresi dolmuş olabilir.")
            }
            if (code !in 200..299) throw IOException("HTTP $code: $urlString")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPost(urlString: String, body: String, authToken: String?): String {
        require(urlString.startsWith("https://")) { "Sadece HTTPS: $urlString" }
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        if (!authToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $authToken")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = try { connection.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() } catch (_: Exception) { null }
                throw IOException("HTTP $code: $urlString${if (!err.isNullOrBlank()) " — $err" else ""}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
