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

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SABAH_NAMAZI_ENABLED = "sabah_namazi_enabled"
    }
}
