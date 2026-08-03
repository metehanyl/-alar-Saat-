package com.metehanyl.calarsaat.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmScheduler
import com.metehanyl.calarsaat.alarm.AlarmSounds
import com.metehanyl.calarsaat.alarm.AlarmTonePlayer
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.AlarmGroupEntity
import com.metehanyl.calarsaat.data.PinHasher
import com.metehanyl.calarsaat.databinding.ActivityGroupEditBinding
import com.metehanyl.calarsaat.util.DayUtils
import kotlinx.coroutines.launch
import java.util.Calendar

class GroupEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupEditBinding
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val groupDao by lazy { AlarmDatabase.getInstance(this).alarmGroupDao() }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val tonePlayer = AlarmTonePlayer()

    private val times = mutableListOf<Pair<Int, Int>>()
    private lateinit var adapter: GroupTimeAdapter
    private val dayChips = mutableMapOf<Int, Chip>()
    private lateinit var melodyButtons: Map<Int, RadioButton>
    private var editingGroup: AlarmGroupEntity? = null
    private var existingPinHash: String? = null
    private var existingPatternHash: String? = null
    private var existingTextPassHash: String? = null
    private var drawnPattern: List<Int>? = null
    private var previewPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        buildDayChips()
        setupMelodyOptions()
        setupVolumeSlider()
        setupLockCheckboxes()

        adapter = GroupTimeAdapter(times) { position ->
            times.removeAt(position)
            adapter.notifyItemRemoved(position)
            updateEmptyHint()
        }
        binding.recyclerTimes.layoutManager = LinearLayoutManager(this)
        binding.recyclerTimes.adapter = adapter

        val groupId = intent.getIntExtra(EXTRA_GROUP_ID, -1)
        if (groupId != -1) {
            binding.toolbar.title = getString(R.string.edit_group_title)
            binding.buttonDeleteGroup.visibility = View.VISIBLE
            lifecycleScope.launch {
                val group = groupDao.getById(groupId)
                if (group != null) {
                    editingGroup = group
                    binding.editGroupName.setText(group.name)
                    val alarms = dao.getByGroupId(groupId)
                    times.addAll(alarms.map { it.hour to it.minute })
                    times.sortWith(compareBy({ it.first }, { it.second }))
                    adapter.notifyDataSetChanged()
                    updateEmptyHint()

                    val first = alarms.firstOrNull()
                    if (first != null) {
                        val days = first.repeatDaysSet()
                        for ((dayValue, chip) in dayChips) {
                            chip.isChecked = dayValue in days
                        }
                        melodyButtons[first.soundId]?.isChecked = true
                        existingPinHash = first.pinHash
                        existingPatternHash = first.patternHash
                        existingTextPassHash = first.textPassHash
                        binding.seekGroupVolume.progress = first.volume
                        binding.textGroupVolumeValue.text =
                            getString(R.string.volume_value_format, first.volume)

                        val steps = first.effectiveLockSteps()
                        if ("pin" in steps) {
                            binding.checkGroupLockPin.isChecked = true
                            binding.layoutGroupLockPin.visibility = View.VISIBLE
                        }
                        if ("pattern" in steps) {
                            binding.checkGroupLockPattern.isChecked = true
                            binding.layoutGroupLockPattern.visibility = View.VISIBLE
                            binding.textGroupPatternStatus.text = getString(R.string.pattern_already_set)
                        }
                        if ("text" in steps) {
                            binding.checkGroupLockText.isChecked = true
                            binding.layoutGroupLockText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        } else {
            binding.toolbar.title = getString(R.string.new_group_title)
        }

        binding.buttonAddTime.setOnClickListener { showTimePicker() }
        binding.buttonSaveGroup.setOnClickListener { onSaveClicked() }
        binding.buttonDeleteGroup.setOnClickListener { onDeleteGroupClicked() }
        binding.buttonPreviewGroupMelody.setOnClickListener { onPreviewClicked() }

        binding.patternLockViewGroup.onPatternComplete = { pattern ->
            drawnPattern = pattern
            binding.textGroupPatternStatus.text = getString(R.string.pattern_set_ok)
        }

        updateEmptyHint()
    }

    private fun setupLockCheckboxes() {
        binding.checkGroupLockPin.setOnCheckedChangeListener { _, checked ->
            binding.layoutGroupLockPin.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.checkGroupLockPattern.setOnCheckedChangeListener { _, checked ->
            binding.layoutGroupLockPattern.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.checkGroupLockText.setOnCheckedChangeListener { _, checked ->
            binding.layoutGroupLockText.visibility = if (checked) View.VISIBLE else View.GONE
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
            binding.chipGroupGroupDays.addView(chip)
        }
    }

    private fun setupMelodyOptions() {
        melodyButtons = mapOf(
            0 to binding.radioGroupMelody0,
            1 to binding.radioGroupMelody1,
            2 to binding.radioGroupMelody2,
            3 to binding.radioGroupMelody3,
            4 to binding.radioGroupMelody4
        )
        binding.radioGroupGroupMelody.setOnCheckedChangeListener { _, _ -> stopPreview() }
    }

    private fun selectedSoundId(): Int =
        melodyButtons.entries.firstOrNull { it.value.isChecked }?.key ?: 0

    private fun setupVolumeSlider() {
        binding.textGroupVolumeValue.text =
            getString(R.string.volume_value_format, binding.seekGroupVolume.progress)
        binding.seekGroupVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.textGroupVolumeValue.text = getString(R.string.volume_value_format, progress)
                if (fromUser) tonePlayer.setVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                previewPlaying = false
                binding.buttonPreviewGroupMelody.setText(R.string.melody_preview)
                tonePlayer.start(AlarmSounds.byId(selectedSoundId()))
                tonePlayer.setVolume(seekBar.progress)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                tonePlayer.stop()
            }
        })
    }

    private fun selectedVolume(): Int = binding.seekGroupVolume.progress.coerceIn(1, 100)

    private fun onPreviewClicked() {
        if (previewPlaying) {
            stopPreview()
        } else {
            tonePlayer.start(AlarmSounds.byId(selectedSoundId()))
            previewPlaying = true
            binding.buttonPreviewGroupMelody.setText(R.string.melody_preview_stop)
        }
    }

    private fun stopPreview() {
        if (previewPlaying) {
            tonePlayer.stop()
            previewPlaying = false
            binding.buttonPreviewGroupMelody.setText(R.string.melody_preview)
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

    private fun showTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                if (times.none { it.first == hour && it.second == minute }) {
                    times.add(hour to minute)
                    times.sortWith(compareBy({ it.first }, { it.second }))
                    adapter.notifyDataSetChanged()
                    updateEmptyHint()
                }
            },
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true
        ).show()
    }

    private fun updateEmptyHint() {
        binding.emptyTimesHint.visibility = if (times.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onSaveClicked() {
        val name = binding.editGroupName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Snackbar.make(binding.root, R.string.group_name_required_error, Snackbar.LENGTH_LONG).show()
            return
        }
        if (times.isEmpty()) {
            Snackbar.make(binding.root, R.string.group_no_times_error, Snackbar.LENGTH_LONG).show()
            return
        }

        val pinChecked = binding.checkGroupLockPin.isChecked
        val patternChecked = binding.checkGroupLockPattern.isChecked
        val textChecked = binding.checkGroupLockText.isChecked

        val enteredPin = binding.editGroupLockPin.text?.toString().orEmpty().trim()
        val enteredText = binding.editGroupLockText.text?.toString().orEmpty()

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

        val soundId = selectedSoundId()
        val volume = selectedVolume()
        val selectedDays = dayChips.filterValues { it.isChecked }.keys
        val repeatDays = AlarmEntity.daysToString(selectedDays)

        lifecycleScope.launch {
            val existingInGroup = editingGroup?.let { dao.getByGroupId(it.id) } ?: emptyList()
            val totalAfterSave = dao.count() - existingInGroup.size + times.size
            if (totalAfterSave > AlarmDatabase.MAX_ALARMS) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.max_alarms_reached, AlarmDatabase.MAX_ALARMS),
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }

            val group = editingGroup?.copy(name = name) ?: AlarmGroupEntity(name = name)
            val groupId = if (editingGroup == null) {
                groupDao.insert(group).toInt()
            } else {
                groupDao.update(group)
                group.id
            }

            for (alarm in existingInGroup) {
                scheduler.cancel(alarm)
                dao.delete(alarm)
            }
            for ((hour, minute) in times) {
                val alarm = AlarmEntity(
                    hour = hour,
                    minute = minute,
                    label = name,
                    groupId = groupId,
                    repeatDays = repeatDays,
                    soundId = soundId,
                    requirePin = pinChecked,
                    pinHash = pinHash,
                    volume = volume,
                    lockType = lockSteps.split(",").firstOrNull() ?: "",
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

    private fun onDeleteGroupClicked() {
        val group = editingGroup ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.group_delete_confirm_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    for (alarm in dao.getByGroupId(group.id)) {
                        scheduler.cancel(alarm)
                        dao.delete(alarm)
                    }
                    groupDao.delete(group)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val TEXT_MIN_PASS_LENGTH = 4
        const val TEXT_MAX_PASS_LENGTH = 10
    }
}
