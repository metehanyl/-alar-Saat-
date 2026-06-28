package com.metehanyl.calarsaat.prayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Son başarıyla çekilen vakit verisini diske yazar. İnternet olmadığında en son
 * kaydedilen veri kullanılır.
 */
class PrayerCache(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(bundle: PrayerBundle) {
        val daysArray = JSONArray()
        bundle.days.forEach { day ->
            daysArray.put(
                JSONObject().apply {
                    put("tarih", day.tarih)
                    put("imsak", day.imsak)
                    put("gunes", day.gunes)
                    put("ogle", day.ogle)
                    put("ikindi", day.ikindi)
                    put("aksam", day.aksam)
                    put("yatsi", day.yatsi)
                }
            )
        }
        val obj = JSONObject().apply {
            put("sehirId", bundle.sehirId)
            put("sehirAdi", bundle.sehirAdi)
            put("ilceId", bundle.ilceId)
            put("ilceAdi", bundle.ilceAdi)
            put("fetchedAt", bundle.fetchedAtEpochMillis)
            put("days", daysArray)
        }
        prefs.edit().putString(KEY_BUNDLE, obj.toString()).apply()
    }

    fun load(): PrayerBundle? {
        val raw = prefs.getString(KEY_BUNDLE, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val daysArr = obj.getJSONArray("days")
            val days = buildList {
                for (i in 0 until daysArr.length()) {
                    val d = daysArr.getJSONObject(i)
                    add(
                        PrayerDay(
                            tarih = d.getString("tarih"),
                            imsak = d.getString("imsak"),
                            gunes = d.getString("gunes"),
                            ogle = d.getString("ogle"),
                            ikindi = d.getString("ikindi"),
                            aksam = d.getString("aksam"),
                            yatsi = d.getString("yatsi")
                        )
                    )
                }
            }
            if (days.isEmpty()) return null
            PrayerBundle(
                sehirId = obj.getString("sehirId"),
                sehirAdi = obj.getString("sehirAdi"),
                ilceId = obj.getString("ilceId"),
                ilceAdi = obj.getString("ilceAdi"),
                fetchedAtEpochMillis = obj.getLong("fetchedAt"),
                days = days
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "calar_saat_prayer_cache"
        private const val KEY_BUNDLE = "prayer_bundle"
    }
}
