package com.metehanyl.calarsaat.ui

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmScheduler
import com.metehanyl.calarsaat.alarm.AlarmSounds
import com.metehanyl.calarsaat.alarm.AlarmTonePlayer
import com.google.android.material.snackbar.Snackbar
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.databinding.ActivityAddEditAlarmBinding
import com.metehanyl.calarsaat.util.DayUtils
import kotlinx.coroutines.launch

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAlarmBinding
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val tonePlayer = AlarmTonePlayer()

    private var editingAlarm: AlarmEntity? = null
    private val dayChips = mutableMapOf<Int, Chip>()
    private lateinit var melodyButtons: Map<Int, RadioButton>
    private var previewPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.timePicker.setIs24HourView(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        buildDayChips()
        setupMelodyOptions()
        setupVolumeSlider()

        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId != -1) {
            binding.toolbar.title = getString(R.string.edit_alarm_title)
            binding.buttonDelete.visibility = android.view.View.VISIBLE
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
        binding.switchRequirePin.setOnCheckedChangeListener { _, checked ->
            binding.layoutAlarmPin.visibility = if (checked) android.view.View.VISIBLE else android.view.View.GONE
        }
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
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.timePicker.hour = alarm.hour
            binding.timePicker.minute = alarm.minute
        } else {
            @Suppress("DEPRECATION")
            binding.timePicker.currentHour = alarm.hour
            @Suppress("DEPRECATION")
            binding.timePicker.currentMinute = alarm.minute
        }
        binding.editLabel.setText(alarm.label)
        val days = alarm.repeatDaysSet()
        for ((dayValue, chip) in dayChips) {
            chip.isChecked = dayValue in days
        }
        melodyButtons[alarm.soundId]?.isChecked = true
        binding.seekVolume.progress = alarm.volume
        binding.textVolumeValue.text = getString(R.string.volume_value_format, alarm.volume)
        binding.switchRequirePin.isChecked = alarm.requirePin
        binding.layoutAlarmPin.visibility = if (alarm.requirePin) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun onSaveClicked() {
        val hour: Int
        val minute: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hour = binding.timePicker.hour
            minute = binding.timePicker.minute
        } else {
            @Suppress("DEPRECATION")
            hour = binding.timePicker.currentHour
            @Suppress("DEPRECATION")
            minute = binding.timePicker.currentMinute
        }
        val label = binding.editLabel.text?.toString().orEmpty().trim()
        val selectedDays = dayChips.filterValues { it.isChecked }.keys

        val requirePin = binding.switchRequirePin.isChecked
        val enteredPin = binding.editAlarmPin.text?.toString().orEmpty().trim()
        if (requirePin && enteredPin.isNotEmpty() && enteredPin.length < 4) {
            Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
            return
        }
        if (requirePin && enteredPin.isEmpty() && editingAlarm?.pinHash == null) {
            Snackbar.make(binding.root, R.string.pin_required_error, Snackbar.LENGTH_LONG).show()
            return
        }
        val pinHash = when {
            !requirePin -> null
            enteredPin.isNotEmpty() -> PinHasher.hash(enteredPin)
            else -> editingAlarm?.pinHash
        }

        val alarm = (editingAlarm ?: AlarmEntity(hour = hour, minute = minute, label = label)).copy(
            hour = hour,
            minute = minute,
            label = label,
            repeatDays = AlarmEntity.daysToString(selectedDays),
            soundId = selectedSoundId(),
            enabled = true,
            requirePin = requirePin,
            pinHash = pinHash,
            volume = selectedVolume()
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
