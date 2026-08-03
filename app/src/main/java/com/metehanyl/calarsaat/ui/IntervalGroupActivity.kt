package com.metehanyl.calarsaat.ui

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.metehanyl.calarsaat.data.IntervalAlarmGroupEntity
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.databinding.ActivityIntervalGroupBinding
import com.metehanyl.calarsaat.util.DayUtils
import kotlinx.coroutines.launch
import java.util.Calendar

class IntervalGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntervalGroupBinding
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val intervalGroupDao by lazy { AlarmDatabase.getInstance(this).intervalAlarmGroupDao() }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val tonePlayer = AlarmTonePlayer()

    private val dayChips = mutableMapOf<Int, Chip>()
    private lateinit var melodyButtons: Map<Int, RadioButton>
    private var editingGroup: IntervalAlarmGroupEntity? = null
    private var startHour = 7
    private var startMinute = 0
    private var drawnPattern: List<Int>? = null
    private var existingPinHash: String? = null
    private var existingPatternHash: String? = null
    private var existingTextPassHash: String? = null
    private var previewPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntervalGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        buildDayChips()
        setupMelodyOptions()
        setupVolumeSlider()
        setupLockCheckboxes()

        binding.textIntervalStartTime.setOnClickListener { showTimePicker() }
        updateTimeDisplay()

        val groupId = intent.getIntExtra(EXTRA_INTERVAL_GROUP_ID, -1)
        if (groupId != -1) {
            binding.toolbar.title = getString(R.string.edit_interval_group_title)
            binding.buttonDeleteGroup.visibility = View.VISIBLE
            lifecycleScope.launch {
                val group = intervalGroupDao.getById(groupId)
                if (group != null) {
                    editingGroup = group
                    bindGroupToForm(group)
                }
            }
        } else {
            binding.toolbar.title = getString(R.string.new_interval_group_title)
        }

        binding.buttonSaveGroup.setOnClickListener { onSaveClicked() }
        binding.buttonDeleteGroup.setOnClickListener { onDeleteClicked() }
        binding.buttonPreviewMelody.setOnClickListener { onPreviewClicked() }

        binding.patternLockView.onPatternComplete = { pattern ->
            drawnPattern = pattern
            binding.textPatternStatus.text = getString(R.string.pattern_set_ok)
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
                if (fromUser) tonePlayer.setVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                previewPlaying = false
                binding.buttonPreviewMelody.setText(R.string.melody_preview)
                tonePlayer.start(AlarmSounds.byId(selectedSoundId()))
                tonePlayer.setVolume(seekBar.progress)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) { tonePlayer.stop() }
        })
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
        binding.textIntervalStartTime.text = "%02d:%02d".format(startHour, startMinute)
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(startHour)
            .setMinute(startMinute)
            .setTitleText(getString(R.string.interval_start_time_label))
            .build()
        picker.addOnPositiveButtonClickListener {
            startHour = picker.hour
            startMinute = picker.minute
            updateTimeDisplay()
        }
        picker.show(supportFragmentManager, "intervalTimePicker")
    }

    private fun selectedVolume(): Int = binding.seekVolume.progress.coerceIn(1, 100)

    private fun onPreviewClicked() {
        if (previewPlaying) stopPreview()
        else {
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

    private fun bindGroupToForm(group: IntervalAlarmGroupEntity) {
        startHour = group.startHour
        startMinute = group.startMinute
        updateTimeDisplay()

        binding.editGroupName.setText(group.name)
        binding.editIntervalMinutes.setText(group.intervalMinutes.toString())
        binding.editAlarmCount.setText(group.alarmCount.toString())

        val days = group.repeatDaysSet()
        for ((dayValue, chip) in dayChips) {
            chip.isChecked = dayValue in days
        }
        melodyButtons[group.soundId]?.isChecked = true
        binding.seekVolume.progress = group.volume
        binding.textVolumeValue.text = getString(R.string.volume_value_format, group.volume)

        existingPinHash = group.pinHash
        existingPatternHash = group.patternHash
        existingTextPassHash = group.textPassHash

        val steps = group.lockStepsList()
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
        val name = binding.editGroupName.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Snackbar.make(binding.root, R.string.group_name_required_error, Snackbar.LENGTH_LONG).show()
            return
        }

        val intervalMinutes = binding.editIntervalMinutes.text?.toString()?.toIntOrNull()
        if (intervalMinutes == null || intervalMinutes < 1) {
            Snackbar.make(binding.root, R.string.interval_minutes_error, Snackbar.LENGTH_LONG).show()
            return
        }

        val alarmCount = binding.editAlarmCount.text?.toString()?.toIntOrNull()
        if (alarmCount == null || alarmCount !in 1..1000) {
            Snackbar.make(binding.root, R.string.interval_count_error, Snackbar.LENGTH_LONG).show()
            return
        }

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

        val selectedDays = dayChips.filterValues { it.isChecked }.keys
        val repeatDays = AlarmEntity.daysToString(selectedDays)

        val group = (editingGroup ?: IntervalAlarmGroupEntity(
            name = name, startHour = startHour, startMinute = startMinute
        )).copy(
            name = name,
            startHour = startHour,
            startMinute = startMinute,
            intervalMinutes = intervalMinutes,
            alarmCount = alarmCount,
            repeatDays = repeatDays,
            soundId = selectedSoundId(),
            volume = selectedVolume(),
            lockSteps = lockSteps,
            pinHash = pinHash,
            patternHash = patternHash,
            textPassHash = textPassHash
        )

        lifecycleScope.launch {
            editingGroup?.let { existing ->
                for (alarm in dao.getByIntervalGroupId(existing.id)) {
                    scheduler.cancel(alarm)
                    dao.delete(alarm)
                }
            }

            val savedGroupId = if (editingGroup == null) {
                intervalGroupDao.insert(group).toInt()
            } else {
                intervalGroupDao.update(group)
                group.id
            }

            for (i in 0 until alarmCount) {
                val totalMin = startHour * 60 + startMinute + i * intervalMinutes
                val alarmHour = (totalMin / 60) % 24
                val alarmMinute = totalMin % 60

                val alarm = AlarmEntity(
                    hour = alarmHour,
                    minute = alarmMinute,
                    label = name,
                    repeatDays = repeatDays,
                    soundId = selectedSoundId(),
                    volume = selectedVolume(),
                    intervalGroupId = savedGroupId,
                    requirePin = pinChecked,
                    pinHash = pinHash,
                    patternHash = patternHash,
                    textPassHash = textPassHash,
                    lockSteps = lockSteps
                )
                val id = dao.insert(alarm)
                val saved = alarm.copy(id = id.toInt())
                val next = scheduler.schedule(saved)
                dao.update(saved.copy(nextTriggerAtMillis = next))
            }
            finish()
        }
    }

    private fun onDeleteClicked() {
        val group = editingGroup ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.interval_group_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    for (alarm in dao.getByIntervalGroupId(group.id)) {
                        scheduler.cancel(alarm)
                        dao.delete(alarm)
                    }
                    intervalGroupDao.delete(group)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_INTERVAL_GROUP_ID = "extra_interval_group_id"
        const val TEXT_MIN_PASS_LENGTH = 4
        const val TEXT_MAX_PASS_LENGTH = 10
    }
}
