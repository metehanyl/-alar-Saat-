package com.metehanyl.calarsaat.data

import android.content.Context

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, PinHasher.hash(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == PinHasher.hash(pin)
    }

    fun isSabahNamaziEnabled(): Boolean = prefs.getBoolean(KEY_SABAH_NAMAZI_ENABLED, false)

    fun setSabahNamaziEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SABAH_NAMAZI_ENABLED, enabled).apply()
    }

    fun getSabahNamaziAlarmCount(): Int = prefs.getInt(KEY_SABAH_COUNT, DEFAULT_SABAH_COUNT)

    fun setSabahNamaziAlarmCount(count: Int) {
        prefs.edit().putInt(KEY_SABAH_COUNT, count).apply()
    }

    fun getSabahNamaziIntervalMinutes(): Int = prefs.getInt(KEY_SABAH_INTERVAL, DEFAULT_SABAH_INTERVAL)

    fun setSabahNamaziIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SABAH_INTERVAL, minutes).apply()
    }

    fun getSabahNamaziOffsetMinutes(): Int = prefs.getInt(KEY_SABAH_OFFSET, DEFAULT_SABAH_OFFSET)

    fun setSabahNamaziOffsetMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SABAH_OFFSET, minutes).apply()
    }

    fun isSabahNamaziPinRequired(): Boolean = prefs.getBoolean(KEY_SABAH_REQUIRE_PIN, false)

    fun setSabahNamaziPinRequired(required: Boolean) {
        prefs.edit().putBoolean(KEY_SABAH_REQUIRE_PIN, required).apply()
    }

    fun getSabahNamaziPinHash(): String? = prefs.getString(KEY_SABAH_PIN_HASH, null)

    fun setSabahNamaziPinHash(hash: String?) {
        prefs.edit().putString(KEY_SABAH_PIN_HASH, hash).apply()
    }

    fun getSabahNamaziVolume(): Int = prefs.getInt(KEY_SABAH_VOLUME, DEFAULT_SABAH_VOLUME)

    fun setSabahNamaziVolume(volume: Int) {
        prefs.edit().putInt(KEY_SABAH_VOLUME, volume).apply()
    }

    fun isOemAutostartPromptShown(): Boolean = prefs.getBoolean(KEY_OEM_AUTOSTART_PROMPT_SHOWN, false)

    fun setOemAutostartPromptShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_OEM_AUTOSTART_PROMPT_SHOWN, shown).apply()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SABAH_NAMAZI_ENABLED = "sabah_namazi_enabled"
        private const val KEY_SABAH_COUNT = "sabah_namazi_count"
        private const val KEY_SABAH_INTERVAL = "sabah_namazi_interval"
        private const val KEY_SABAH_OFFSET = "sabah_namazi_offset"
        private const val KEY_SABAH_REQUIRE_PIN = "sabah_namazi_require_pin"
        private const val KEY_SABAH_PIN_HASH = "sabah_namazi_pin_hash"
        private const val KEY_SABAH_VOLUME = "sabah_namazi_volume"
        private const val KEY_OEM_AUTOSTART_PROMPT_SHOWN = "oem_autostart_prompt_shown"

        // Default settings reproduce the original fixed behavior: imsak+10, +14, +18.
        const val DEFAULT_SABAH_COUNT = 3
        const val DEFAULT_SABAH_INTERVAL = 4
        const val DEFAULT_SABAH_OFFSET = 10
        const val DEFAULT_SABAH_VOLUME = 100
    }
}
