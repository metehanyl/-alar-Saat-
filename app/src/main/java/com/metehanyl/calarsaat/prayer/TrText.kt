package com.metehanyl.calarsaat.prayer

import java.util.Locale

/**
 * Türkçe il/ilçe adlarını karşılaştırmak için diyakritiksiz, büyük harfli anahtara çevirir.
 * Geocoder'dan gelen ad ile Diyanet API'sinden gelen ad farklı yazılsa da eşleşsin diye kullanılır.
 */
fun trKey(input: String): String {
    var result = input.trim().uppercase(Locale("tr", "TR"))
    val replacements = listOf(
        "Ç" to "C", "Ğ" to "G", "İ" to "I", "Ö" to "O", "Ş" to "S", "Ü" to "U"
    )
    for ((from, to) in replacements) {
        result = result.replace(from, to)
    }
    return result.replace(" ", "").replace("'", "")
}
