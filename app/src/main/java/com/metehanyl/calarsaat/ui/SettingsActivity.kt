package com.metehanyl.calarsaat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.data.PrefsManager
import com.metehanyl.calarsaat.databinding.ActivitySettingsBinding
import com.metehanyl.calarsaat.prayer.DiyanetApi
import com.metehanyl.calarsaat.util.OemPermissionHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { PrefsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonSavePin.setOnClickListener { onSavePinClicked() }
        binding.buttonOemAutostart.setOnClickListener {
            OemPermissionHelper.openAutoStartSettings(this)
        }

        // Kayıtlı kimlik bilgilerini göster (şifre hariç)
        binding.editDiyanetEmail.setText(prefs.getDiyanetEmail())

        binding.buttonSaveDiyanet.setOnClickListener { onSaveDiyanetClicked() }
    }

    private fun onSaveDiyanetClicked() {
        val email = binding.editDiyanetEmail.text?.toString().orEmpty().trim()
        val password = binding.editDiyanetPassword.text?.toString().orEmpty()
        prefs.setDiyanetEmail(email)
        prefs.setDiyanetPassword(password)
        DiyanetApi.invalidateToken()
        Snackbar.make(binding.root, R.string.settings_diyanet_saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun onSavePinClicked() {
        val pin = binding.editPin.text?.toString().orEmpty()
        val confirm = binding.editPinConfirm.text?.toString().orEmpty()

        if (pin.length < 4) {
            Snackbar.make(binding.root, R.string.settings_pin_too_short, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (pin != confirm) {
            Snackbar.make(binding.root, R.string.settings_pin_mismatch, Snackbar.LENGTH_SHORT).show()
            return
        }

        prefs.setPin(pin)
        binding.editPin.text?.clear()
        binding.editPinConfirm.text?.clear()
        Snackbar.make(binding.root, R.string.settings_pin_saved, Snackbar.LENGTH_SHORT).show()
    }
}
