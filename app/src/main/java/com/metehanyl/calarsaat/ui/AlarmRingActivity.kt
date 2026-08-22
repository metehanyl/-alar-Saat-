package com.metehanyl.calarsaat.ui

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.metehanyl.calarsaat.AlarmApp
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmRingingService
import com.metehanyl.calarsaat.alarm.AlarmScheduler
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_ID
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_LABEL
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_LOCK_STEPS
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_LOCK_TYPE
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_PATTERN_HASH
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_PIN_HASH
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_REQUIRE_PIN
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_SOUND_ID
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_TEXT_PASS_HASH
import com.metehanyl.calarsaat.alarm.EXTRA_ALARM_VOLUME
import com.metehanyl.calarsaat.alarm.SnoozeCancelReceiver
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.databinding.ActivityAlarmRingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import kotlin.math.abs

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding
    private val enteredPin = StringBuilder()
    private val maxPinLength = 6
    private var pinHash: String? = null
    private var patternHash: String? = null
    private var textPassHash: String? = null
    private var lockSteps: List<String> = emptyList()
    private var currentLockStep = 0
    private var alarmId = -1
    private var soundId = 0
    private var volume = 100
    private var requirePin = false

    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                if (abs(deltaX) <= abs(deltaY) || abs(deltaX) < SWIPE_DISTANCE_THRESHOLD ||
                    abs(velocityX) < SWIPE_VELOCITY_THRESHOLD
                ) {
                    return false
                }
                if (deltaX > 0) onSwipeRight() else onSwipeLeft()
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenWindow()
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* block back press while ringing */ }
        })

        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        soundId = intent.getIntExtra(EXTRA_ALARM_SOUND_ID, 0)
        volume = intent.getIntExtra(EXTRA_ALARM_VOLUME, 100)
        requirePin = intent.getBooleanExtra(EXTRA_ALARM_REQUIRE_PIN, false)
        pinHash = intent.getStringExtra(EXTRA_ALARM_PIN_HASH)
        patternHash = intent.getStringExtra(EXTRA_ALARM_PATTERN_HASH)
        textPassHash = intent.getStringExtra(EXTRA_ALARM_TEXT_PASS_HASH)

        val rawSteps = intent.getStringExtra(EXTRA_ALARM_LOCK_STEPS).orEmpty()
        lockSteps = if (rawSteps.isNotEmpty()) {
            rawSteps.split(",").filter { it.isNotEmpty() }
        } else {
            val rawLockType = intent.getStringExtra(EXTRA_ALARM_LOCK_TYPE).orEmpty()
            when {
                rawLockType.isNotEmpty() -> listOf(rawLockType)
                requirePin -> listOf("pin")
                else -> emptyList()
            }
        }

        // Override lock data from DB to ensure we always have the latest configuration
        if (alarmId != -1) {
            runBlocking(Dispatchers.IO) {
                val alarm = AlarmDatabase.getInstance(this@AlarmRingActivity).alarmDao().getById(alarmId)
                if (alarm != null) {
                    lockSteps = alarm.effectiveLockSteps()
                    pinHash = alarm.pinHash
                    patternHash = alarm.patternHash
                    textPassHash = alarm.textPassHash
                    requirePin = alarm.requirePin
                }
            }
        }

        val label = intent.getStringExtra(EXTRA_ALARM_LABEL).orEmpty()
        binding.textRingLabel.text = label
        binding.textRingLabel.visibility = if (label.isBlank()) View.GONE else View.VISIBLE
        binding.textRingTime.text = currentTimeText()

        if (lockSteps.isEmpty()) {
            binding.textSwipeDismissHint.visibility = View.VISIBLE
        } else {
            binding.textSwipeDismissHint.visibility = View.GONE
            showCurrentLockStep()
        }

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
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

    private fun showCurrentLockStep() {
        binding.layoutPinSection.visibility = View.GONE
        binding.layoutPatternSection.visibility = View.GONE

        val step = lockSteps.getOrNull(currentLockStep) ?: run { dismissAlarm(); return }
        when (step) {
            "pin" -> {
                binding.layoutPinSection.visibility = View.VISIBLE
                enteredPin.clear()
                refreshPinDisplay()
                binding.textRingError.visibility = View.INVISIBLE
                setupPinPad()
            }
            "pattern" -> {
                binding.layoutPatternSection.visibility = View.VISIBLE
                binding.patternLockRing.clear()
                binding.textPatternError.visibility = View.INVISIBLE
                setupPatternLock()
            }
            "text" -> showTextPasswordDialog()
        }
    }

    private fun onStepPassed() {
        currentLockStep++
        if (currentLockStep >= lockSteps.size) {
            dismissAlarm()
        } else {
            showCurrentLockStep()
        }
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
        binding.textRingError.visibility = View.INVISIBLE

        if (enteredPin.length >= 4) {
            if (pinHash == PinHasher.hash(enteredPin.toString())) {
                onStepPassed()
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
        binding.textRingError.visibility = View.VISIBLE
        enteredPin.clear()
        refreshPinDisplay()
    }

    private fun refreshPinDisplay() {
        binding.textPinDots.text = "● ".repeat(enteredPin.length).trim()
    }

    private fun setupPatternLock() {
        binding.patternLockRing.onPatternComplete = { pattern ->
            val hash = PinHasher.hash(pattern.joinToString(","))
            if (hash == patternHash) {
                onStepPassed()
            } else {
                binding.textPatternError.visibility = View.VISIBLE
                binding.patternLockRing.postDelayed({
                    binding.textPatternError.visibility = View.INVISIBLE
                }, 1500)
            }
        }
    }

    private fun showTextPasswordDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.text_pass_hint)
            setPadding(48, 24, 48, 24)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.ring_dismiss_hint_text))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ring_confirm_button), null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val entered = input.text.toString()
            if (PinHasher.hash(entered) == textPassHash) {
                dialog.dismiss()
                onStepPassed()
            } else {
                input.text.clear()
                input.error = getString(R.string.ring_text_wrong)
            }
        }
    }

    private fun onSwipeRight() {
        if (lockSteps.isEmpty()) dismissAlarm()
    }

    private fun onSwipeLeft() {
        snoozeAlarm()
    }

    private fun dismissAlarm() {
        hideKeyboard()
        AlarmRingingService.stop(this)
        finish()
    }

    private fun snoozeAlarm() {
        AlarmRingingService.stop(this)
        if (alarmId != -1) {
            val triggerAt = AlarmScheduler(this).scheduleSnooze(
                alarmId = alarmId,
                label = intent.getStringExtra(EXTRA_ALARM_LABEL).orEmpty(),
                soundId = soundId,
                requirePin = requirePin,
                pinHash = pinHash,
                volume = volume,
                lockType = lockSteps.firstOrNull() ?: "",
                patternHash = patternHash,
                textPassHash = textPassHash,
                lockSteps = lockSteps.joinToString(",")
            )
            showSnoozeNotification(triggerAt)
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AlarmDatabase.getInstance(this@AlarmRingActivity).alarmDao()
                val alarm = dao.getById(alarmId)
                if (alarm != null) {
                    dao.update(alarm.copy(isSnoozed = true, snoozedUntilMillis = triggerAt))
                }
            }
        }
        finish()
    }

    private fun showSnoozeNotification(triggerAtMillis: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val timeStr = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

        val cancelIntent = Intent(this, SnoozeCancelReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val cancelPi = PendingIntent.getBroadcast(
            this, alarmId + AlarmScheduler.SNOOZE_NOTIF_ID_OFFSET, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, AlarmApp.CHANNEL_ID_SNOOZE)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.snooze_notification_title))
            .setContentText(getString(R.string.snooze_notification_text, timeStr))
            .setOngoing(true)
            .addAction(0, getString(R.string.snooze_cancel_action), cancelPi)
            .build()

        NotificationManagerCompat.from(this).notify(alarmId + AlarmScheduler.SNOOZE_NOTIF_ID_OFFSET, notif)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    /**
     * Kullanıcı alarm çalarken Giriş/Ana Ekran veya Son Uygulamalar tuşuna bastığında
     * çağrılır. Alarm henüz durdurulmadıysa ekranı hemen geri getirir.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (AlarmRingingService.currentAlarmId != -1) {
            startActivity(
                Intent(this, AlarmRingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AlarmRingingService.stop(this)
        }
    }

    companion object {
        private const val SWIPE_DISTANCE_THRESHOLD = 120
        private const val SWIPE_VELOCITY_THRESHOLD = 150
    }
}
