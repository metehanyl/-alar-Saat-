package com.metehanyl.calarsaat.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmRingingService
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_LABEL
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_PIN_HASH
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_REQUIRE_PIN
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.databinding.ActivityAlarmRingBinding
import java.util.Calendar

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding
    private val enteredPin = StringBuilder()
    private val maxPinLength = 6
    private var pinHash: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenWindow()
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* block back press while ringing */ }
        })

        val label = intent.getStringExtra(EXTRA_ALARM_LABEL).orEmpty()
        binding.textRingLabel.text = label
        binding.textRingLabel.visibility =
            if (label.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        binding.textRingTime.text = currentTimeText()

        val requirePin = intent.getBooleanExtra(EXTRA_ALARM_REQUIRE_PIN, false)
        pinHash = intent.getStringExtra(EXTRA_ALARM_PIN_HASH)
        if (requirePin && pinHash != null) {
            binding.layoutPinSection.visibility = android.view.View.VISIBLE
            binding.buttonDismissPlain.visibility = android.view.View.GONE
            setupPinPad()
        } else {
            binding.layoutPinSection.visibility = android.view.View.GONE
            binding.buttonDismissPlain.visibility = android.view.View.VISIBLE
            binding.buttonDismissPlain.setOnClickListener { dismissAlarm() }
        }
    }

    private fun currentTimeText(): String {
        val now = Calendar.getInstance()
        return "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    }

    private fun setupLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupPinPad() {
        val digitButtons = mapOf(
            binding.button0 to "0", binding.button1 to "1", binding.button2 to "2",
            binding.button3 to "3", binding.button4 to "4", binding.button5 to "5",
            binding.button6 to "6", binding.button7 to "7", binding.button8 to "8",
            binding.button9 to "9"
        )
        for ((button, digit) in digitButtons) {
            button.setOnClickListener { onDigitEntered(digit) }
        }
        binding.buttonBackspace.setOnClickListener { onBackspace() }
        refreshPinDisplay()
    }

    private fun onDigitEntered(digit: String) {
        if (enteredPin.length >= maxPinLength) return
        enteredPin.append(digit)
        refreshPinDisplay()
        binding.textRingError.visibility = android.view.View.INVISIBLE

        if (enteredPin.length >= 4) {
            if (pinHash == PinHasher.hash(enteredPin.toString())) {
                dismissAlarm()
                return
            }
            if (enteredPin.length == maxPinLength) {
                showWrongPin()
            }
        }
    }

    private fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin.deleteCharAt(enteredPin.length - 1)
            refreshPinDisplay()
        }
    }

    private fun showWrongPin() {
        binding.textRingError.visibility = android.view.View.VISIBLE
        enteredPin.clear()
        refreshPinDisplay()
    }

    private fun refreshPinDisplay() {
        binding.textPinDots.text = "● ".repeat(enteredPin.length).trim()
    }

    private fun dismissAlarm() {
        AlarmRingingService.stop(this)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AlarmRingingService.stop(this)
        }
    }
}
