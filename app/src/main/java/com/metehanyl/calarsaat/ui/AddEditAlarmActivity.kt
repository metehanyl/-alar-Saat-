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
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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

        binding.radioGroupLockType.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutLockPin.visibility = if (checkedId == R.id.radioLockPin) View.VISIBLE else View.GONE
            binding.layoutLockPattern.visibility = if (checkedId == R.id.radioLockPattern) View.VISIBLE else View.GONE
            binding.layoutLockText.visibility = if (checkedId == R.id.radioLockText) View.VISIBLE else View.GONE
        }

        binding.patternLockView.onPatternComplete = { pattern ->
            drawnPattern = pattern
            binding.textPatternStatus.text = getString(R.string.pattern_set_ok)
        }
    }

    private fun updateTimeDisplay() {
        binding.textTimeDisplay.text = "%02d:%02d".format(currentHour, currentMinute)
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(getString(R.string.new_alarm_title))
            .build()
        picker.addOnPositiveButtonClickListener {
            currentHour = picker.hour
            currentMinute = picker.minute
            updateTimeDisplay()
        }
        picker.show(supportFragmentManager, "timePicker")
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

        when (alarm.effectiveLockType()) {
            "pin" -> {
                binding.radioLockPin.isChecked = true
                binding.layoutLockPin.visibility = View.VISIBLE
            }
            "pattern" -> {
                binding.radioLockPattern.isChecked = true
                binding.layoutLockPattern.visibility = View.VISIBLE
                binding.textPatternStatus.text = getString(R.string.pattern_already_set)
            }
            "text" -> {
                binding.radioLockText.isChecked = true
                binding.layoutLockText.visibility = View.VISIBLE
            }
            else -> binding.radioLockNone.isChecked = true
        }
    }

    private fun onSaveClicked() {
        val label = binding.editLabel.text?.toString().orEmpty().trim()
        val selectedDays = dayChips.filterValues { it.isChecked }.keys

        val lockType = when (binding.radioGroupLockType.checkedRadioButtonId) {
            R.id.radioLockPin -> "pin"
            R.id.radioLockPattern -> "pattern"
            R.id.radioLockText -> "text"
            else -> ""
        }

        val enteredPin = binding.editLockPin.text?.toString().orEmpty().trim()
        val enteredText = binding.editLockText.text?.toString().orEmpty()

        when (lockType) {
            "pin" -> {
                if (enteredPin.isNotEmpty() && enteredPin.length < 4) {
                    Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
                    return
                }
                if (enteredPin.isEmpty() && existingPinHash == null) {
                    Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
                    return
                }
            }
            "pattern" -> {
                if (drawnPattern == null && existingPatternHash == null) {
                    Snackbar.make(binding.root, R.string.pattern_not_set_error, Snackbar.LENGTH_LONG).show()
                    return
                }
            }
            "text" -> {
                if (enteredText.isNotEmpty() && enteredText.length < 4) {
                    Snackbar.make(binding.root, R.string.text_pass_too_short_error, Snackbar.LENGTH_LONG).show()
                    return
                }
                if (enteredText.isEmpty() && existingTextPassHash == null) {
                    Snackbar.make(binding.root, R.string.text_pass_too_short_error, Snackbar.LENGTH_LONG).show()
                    return
                }
            }
        }

        val pinHash = when {
            lockType != "pin" -> null
            enteredPin.isNotEmpty() -> PinHasher.hash(enteredPin)
            else -> existingPinHash
        }
        val patternHash = when {
            lockType != "pattern" -> null
            drawnPattern != null -> PinHasher.hash(drawnPattern!!.joinToString(","))
            else -> existingPatternHash
        }
        val textPassHash = when {
            lockType != "text" -> null
            enteredText.isNotEmpty() -> PinHasher.hash(enteredText)
            else -> existingTextPassHash
        }

        val alarm = (editingAlarm ?: AlarmEntity(hour = currentHour, minute = currentMinute, label = label)).copy(
            hour = currentHour,
            minute = currentMinute,
            label = label,
            repeatDays = AlarmEntity.daysToString(selectedDays),
            soundId = selectedSoundId(),
            enabled = true,
            requirePin = lockType == "pin",
            pinHash = pinHash,
            volume = selectedVolume(),
            lockType = lockType,
            patternHash = patternHash,
            textPassHash = textPassHash
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
    }
}
