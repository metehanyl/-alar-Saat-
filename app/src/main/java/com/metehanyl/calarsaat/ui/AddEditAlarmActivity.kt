package com.metehanyl.calarsaat.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.view.Gravity
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmScheduler
import com.metehanyl.calarsaat.alarm.AlarmSounds
import com.metehanyl.calarsaat.alarm.AlarmTonePlayer
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.databinding.ActivityAddEditAlarmBinding
import com.metehanyl.calarsaat.util.DayUtils
import kotlinx.coroutines.launch
import java.util.Calendar

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAlarmBinding
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val tonePlayer = AlarmTonePlayer()

    private var editingAlarm: AlarmEntity? = null
    private val dayChips = mutableMapOf<Int, Chip>()
    private lateinit var melodyButtons: Map<Int, RadioButton>
    private var previewPlaying = false
    private var currentHour = 7
    private var currentMinute = 0
    private var drawnPattern: List<Int>? = null
    private var existingPinHash: String? = null
    private var existingPatternHash: String? = null
    private var existingTextPassHash: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val now = Calendar.getInstance()
        currentHour = now.get(Calendar.HOUR_OF_DAY)
        currentMinute = now.get(Calendar.MINUTE)
        updateTimeDisplay()

        binding.toolbar.setNavigationOnClickListener { finish() }
        buildDayChips()
        setupMelodyOptions()
        setupVolumeSlider()
        setupLockCheckboxes()

        binding.textTimeDisplay.setOnClickListener { showTimePicker() }

        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId != -1) {
            binding.toolbar.title = getString(R.string.edit_alarm_title)
            binding.buttonDelete.visibility = View.VISIBLE
            lifecycleScope.launch {
                val alarm = dao.getById(alarmId)
                if (alarm != null) {
                    editingAlarm = alarm
                    bindAlarmToForm(alarm)
                }
            }
        } else {
            binding.toolbar.title = getString(R.string.new_alarm_title)
        }

        binding.buttonSave.setOnClickListener { onSaveClicked() }
        binding.buttonDelete.setOnClickListener { onDeleteClicked() }
        binding.buttonPreviewMelody.setOnClickListener { onPreviewClicked() }

        binding.patternLockView.onPatternComplete = { pattern ->
            drawnPattern = pattern
            binding.textPatternStatus.text = getString(R.string.pattern_set_ok)
        }
    }

    private fun setupLockCheckboxes() {
        binding.checkLockPin.setOnCheckedChangeListener { _, checked ->
            binding.layoutLockPin.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.checkLockPattern.setOnCheckedChangeListener { _, checked ->
            binding.layoutLockPattern.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.checkLockText.setOnCheckedChangeListener { _, checked ->
            binding.layoutLockText.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }

    private fun updateTimeDisplay() {
        binding.textTimeDisplay.text = "%02d:%02d".format(currentHour, currentMinute)
    }

    private fun showTimePicker() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(64, 32, 64, 16)
        }
        val npHour = NumberPicker(this).apply {
            minValue = 0; maxValue = 23; value = currentHour
            setFormatter { "%02d".format(it) }
        }
        val colon = android.widget.TextView(this).apply {
            text = ":"; textSize = 28f; setPadding(16, 0, 16, 0)
        }
        val npMinute = NumberPicker(this).apply {
            minValue = 0; maxValue = 59; value = currentMinute
            setFormatter { "%02d".format(it) }
        }
        container.addView(npHour)
        container.addView(colon)
        container.addView(npMinute)
        AlertDialog.Builder(this)
            .setTitle(R.string.select_time_title)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                currentHour = npHour.value
                currentMinute = npMinute.value
                updateTimeDisplay()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun buildDayChips() {
        for ((dayValue, label) in DayUtils.orderedDays) {
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isClickable = true
            }
            dayChips[dayValue] = chip
            binding.chipGroupDays.addView(chip)
        }
    }

    private fun setupMelodyOptions() {
        melodyButtons = mapOf(
            0 to binding.radioMelody0,
            1 to binding.radioMelody1,
            2 to binding.radioMelody2,
            3 to binding.radioMelody3,
            4 to binding.radioMelody4
        )
        binding.radioGroupMelody.setOnCheckedChangeListener { _, _ -> stopPreview() }
    }

    private fun selectedSoundId(): Int =
        melodyButtons.entries.firstOrNull { it.value.isChecked }?.key ?: 0

    private fun setupVolumeSlider() {
        binding.textVolumeValue.text = getString(R.string.volume_value_format, binding.seekVolume.progress)
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.textVolumeValue.text = getString(R.string.volume_value_format, progress)
                if (fromUser) tonePlayer.setVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                previewPlaying = false
                binding.buttonPreviewMelody.setText(R.string.melody_preview)
                tonePlayer.start(AlarmSounds.byId(selectedSoundId()))
                tonePlayer.setVolume(seekBar.progress)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                tonePlayer.stop()
            }
        })
    }

    private fun selectedVolume(): Int = binding.seekVolume.progress.coerceIn(1, 100)

    private fun onPreviewClicked() {
        if (previewPlaying) {
            stopPreview()
        } else {
            tonePlayer.start(AlarmSounds.byId(selectedSoundId()))
            previewPlaying = true
            binding.buttonPreviewMelody.setText(R.string.melody_preview_stop)
        }
    }

    private fun stopPreview() {
        if (previewPlaying) {
            tonePlayer.stop()
            previewPlaying = false
            binding.buttonPreviewMelody.setText(R.string.melody_preview)
        }
    }

    override fun onPause() {
        super.onPause()
        stopPreview()
    }

    override fun onDestroy() {
        tonePlayer.stop()
        super.onDestroy()
    }

    private fun bindAlarmToForm(alarm: AlarmEntity) {
        currentHour = alarm.hour
        currentMinute = alarm.minute
        updateTimeDisplay()

        binding.editLabel.setText(alarm.label)
        val days = alarm.repeatDaysSet()
        for ((dayValue, chip) in dayChips) {
            chip.isChecked = dayValue in days
        }
        melodyButtons[alarm.soundId]?.isChecked = true
        binding.seekVolume.progress = alarm.volume
        binding.textVolumeValue.text = getString(R.string.volume_value_format, alarm.volume)

        existingPinHash = alarm.pinHash
        existingPatternHash = alarm.patternHash
        existingTextPassHash = alarm.textPassHash

        val steps = alarm.effectiveLockSteps()
        if ("pin" in steps) {
            binding.checkLockPin.isChecked = true
            binding.layoutLockPin.visibility = View.VISIBLE
        }
        if ("pattern" in steps) {
            binding.checkLockPattern.isChecked = true
            binding.layoutLockPattern.visibility = View.VISIBLE
            binding.textPatternStatus.text = getString(R.string.pattern_already_set)
        }
        if ("text" in steps) {
            binding.checkLockText.isChecked = true
            binding.layoutLockText.visibility = View.VISIBLE
        }
    }

    private fun onSaveClicked() {
        val label = binding.editLabel.text?.toString().orEmpty().trim()
        val selectedDays = dayChips.filterValues { it.isChecked }.keys

        val pinChecked = binding.checkLockPin.isChecked
        val patternChecked = binding.checkLockPattern.isChecked
        val textChecked = binding.checkLockText.isChecked

        val enteredPin = binding.editLockPin.text?.toString().orEmpty().trim()
        val enteredText = binding.editLockText.text?.toString().orEmpty()

        if (pinChecked) {
            if (enteredPin.isNotEmpty() && enteredPin.length < 4) {
                Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
                return
            }
            if (enteredPin.isEmpty() && existingPinHash == null) {
                Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
                return
            }
        }
        if (patternChecked && drawnPattern == null && existingPatternHash == null) {
            Snackbar.make(binding.root, R.string.pattern_not_set_error, Snackbar.LENGTH_LONG).show()
            return
        }
        if (textChecked) {
            if (enteredText.isNotEmpty() && (enteredText.length < TEXT_MIN_PASS_LENGTH || enteredText.length > TEXT_MAX_PASS_LENGTH)) {
                Snackbar.make(binding.root, R.string.text_pass_length_error, Snackbar.LENGTH_LONG).show()
                return
            }
            if (enteredText.isEmpty() && existingTextPassHash == null) {
                Snackbar.make(binding.root, R.string.text_pass_length_error, Snackbar.LENGTH_LONG).show()
                return
            }
        }

        val pinHash = if (pinChecked) {
            if (enteredPin.isNotEmpty()) PinHasher.hash(enteredPin) else existingPinHash
        } else null
        val patternHash = if (patternChecked) {
            if (drawnPattern != null) PinHasher.hash(drawnPattern!!.joinToString(",")) else existingPatternHash
        } else null
        val textPassHash = if (textChecked) {
            if (enteredText.isNotEmpty()) PinHasher.hash(enteredText) else existingTextPassHash
        } else null

        val lockSteps = buildList {
            if (pinChecked) add("pin")
            if (patternChecked) add("pattern")
            if (textChecked) add("text")
        }.joinToString(",")

        val alarm = (editingAlarm ?: AlarmEntity(hour = currentHour, minute = currentMinute, label = label)).copy(
            hour = currentHour,
            minute = currentMinute,
            label = label,
            repeatDays = AlarmEntity.daysToString(selectedDays),
            soundId = selectedSoundId(),
            enabled = true,
            requirePin = pinChecked,
            pinHash = pinHash,
            volume = selectedVolume(),
            lockType = lockSteps.split(",").firstOrNull() ?: "",
            patternHash = patternHash,
            textPassHash = textPassHash,
            lockSteps = lockSteps
        )

        lifecycleScope.launch {
            val id = dao.insert(alarm)
            val saved = if (editingAlarm == null) alarm.copy(id = id.toInt()) else alarm
            val next = scheduler.schedule(saved)
            dao.update(saved.copy(nextTriggerAtMillis = next))
            finish()
        }
    }

    private fun onDeleteClicked() {
        val alarm = editingAlarm ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                scheduler.cancel(alarm)
                lifecycleScope.launch {
                    dao.delete(alarm)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val TEXT_MIN_PASS_LENGTH = 4
        const val TEXT_MAX_PASS_LENGTH = 10
    }
}
