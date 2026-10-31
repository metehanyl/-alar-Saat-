package com.metehanyl.calarsaat.prayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * namazvakitleri.diyanet.gov.tr adresinden kayıt gerektirmeden namaz vakitlerini çeker.
 * İlçe listesi uygulamaya gömülü assets/ilceler.json dosyasından yüklenir.
 */
object DiyanetApi {

    private const val BASE_URL = "https://namazvakitleri.diyanet.gov.tr"

    data class IlData(val name: String, val ilceler: List<IlceData>)
    data class IlceData(val id: Int, val name: String, val slug: String)

    @Volatile private var allIller: List<IlData>? = null

    /** PrayerRepository'nin context'i var, oradan çağrılır. Thread-safe lazy init. */
    fun init(context: Context) {
        if (allIller != null) return
        synchronized(this) {
            if (allIller != null) return
            val json = context.assets.open("ilceler.json").bufferedReader().readText()
            val root = JSONObject(json)
            val illerArr = root.getJSONArray("iller")
            val result = mutableListOf<IlData>()
            for (i in 0 until illerArr.length()) {
                val ilObj = illerArr.getJSONObject(i)
                val ilcelerArr = ilObj.getJSONArray("ilceler")
                val ilceler = buildList {
                    for (j in 0 until ilcelerArr.length()) {
                        val o = ilcelerArr.getJSONObject(j)
                        add(IlceData(
                            id   = o.getInt("id"),
                            name = o.getString("name"),
                            slug = o.getString("slug")
                        ))
                    }
                }
                result.add(IlData(name = ilObj.getString("name"), ilceler = ilceler))
            }
            allIller = result
        }
    }

    /** API'den token gerek yok; uyumluluk için bırakıldı. */
    fun invalidateToken() = Unit

    /** Tüm illeri CityOption listesi olarak döner (id = il adı). */
    suspend fun getCities(): List<CityOption> =
        allIller?.map { CityOption(id = it.name, name = it.name) } ?: emptyList()

    /** Bir ile ait ilçeleri döner (cityId = il adı, DistrictOption.id = Diyanet ilçe numarası). */
    suspend fun getDistricts(cityId: String): List<DistrictOption> {
        val il = allIller?.find { it.name == cityId } ?: return emptyList()
        return il.ilceler.map { DistrictOption(id = it.id.toString(), name = it.name) }
    }

    /**
     * Diyanet web sitesinden ilgili ilçenin aylık vakitlerini çeker ve parse eder.
     * URL: namazvakitleri.diyanet.gov.tr/tr-TR/{id}/{slug}-namaz-vakitleri
     */
    suspend fun getPrayerTimes(districtId: String): List<PrayerDay> = withContext(Dispatchers.IO) {
        val id = districtId.toIntOrNull()
            ?: throw IOException("Geçersiz ilçe numarası: $districtId")
        val ilce = allIller?.flatMap { it.ilceler }?.find { it.id == id }
            ?: throw IOException("İlçe listesinde bulunamadı: $districtId")

        val url = "$BASE_URL/tr-TR/$id/${ilce.slug}-namaz-vakitleri"
        val html = httpGet(url)
        val days = parseHtml(html)
        if (days.isEmpty()) throw IOException("Diyanet sayfasından vakit verisi ayrıştırılamadı.")
        days
    }

    // ── HTML Parser ───────────────────────────────────────────────────────────

    private fun parseHtml(html: String): List<PrayerDay> {
        // Önce <div id="tab-1"> bul, yoksa sayfanın tamamında ara
        val searchFrom = html.indexOf("id=\"tab-1\"").takeIf { it >= 0 } ?: 0

        // thead'den sütun sıralarını tespit et
        val theadStart = html.indexOf("<thead", searchFrom)
        val theadEnd   = html.indexOf("</thead>", theadStart.coerceAtLeast(0))
        val headers: List<String> = if (theadStart in 0 until theadEnd) {
            extractCells(html.substring(theadStart, theadEnd), "th")
                .map { it.lowercase().trim() }
        } else emptyList()

        fun idx(vararg keywords: String) =
            headers.indexOfFirst { h -> keywords.any { h.contains(it) } }.takeIf { it >= 0 }

        val imsakIdx  = idx("imsak")  ?: 1
        val gunesIdx  = idx("güneş", "gunes", "sabah") ?: 2
        val ogleIdx   = idx("öğle", "ogle")  ?: 3
        val ikindiIdx = idx("ikindi") ?: 4
        val aksamIdx  = idx("akşam", "aksam") ?: 5
        val yatsiIdx  = idx("yatsı", "yatsi") ?: 6

        // tbody
        val tbodyStart = html.indexOf("<tbody", searchFrom)
        val tbodyEnd   = html.indexOf("</tbody>", tbodyStart.coerceAtLeast(0))
        if (tbodyStart < 0 || tbodyEnd < 0) return emptyList()
        val tbody = html.substring(tbodyStart, tbodyEnd)

        val rows = mutableListOf<PrayerDay>()
        var pos = 0
        while (true) {
            val trStart = tbody.indexOf("<tr", pos)
            if (trStart < 0) break
            val trEnd = tbody.indexOf("</tr>", trStart)
            if (trEnd < 0) break
            val cells = extractCells(tbody.substring(trStart, trEnd + 5), "td")
            val tarih = cells.getOrElse(0) { "" }.trim()
            if (tarih.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
                rows.add(PrayerDay(
                    tarih   = tarih,
                    imsak   = cells.getOrElse(imsakIdx)  { "" }.trim(),
                    gunes   = cells.getOrElse(gunesIdx)  { "" }.trim(),
                    ogle    = cells.getOrElse(ogleIdx)   { "" }.trim(),
                    ikindi  = cells.getOrElse(ikindiIdx) { "" }.trim(),
                    aksam   = cells.getOrElse(aksamIdx)  { "" }.trim(),
                    yatsi   = cells.getOrElse(yatsiIdx)  { "" }.trim()
                ))
            }
            pos = trEnd + 5
        }
        return rows
    }

    private fun extractCells(html: String, tag: String): List<String> {
        val cells = mutableListOf<String>()
        var pos = 0
        val closeTag = "</$tag>"
        while (true) {
            val start  = html.indexOf("<$tag", pos)
            if (start < 0) break
            val close  = html.indexOf(">", start)
            if (close < 0) break
            val end    = html.indexOf(closeTag, close)
            if (end < 0) break
            val content = html.substring(close + 1, end)
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim()
            cells.add(content)
            pos = end + closeTag.length
        }
        return cells
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout    = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.5")
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 12; SM-G990B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: $urlString")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
