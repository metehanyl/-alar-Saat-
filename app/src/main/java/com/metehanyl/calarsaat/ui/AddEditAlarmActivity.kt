package com.metehanyl.calarsaat.ui

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.alarm.AlarmScheduler
import com.metehanyl.calarsaat.data.AlarmDatabase
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.databinding.ActivityAddEditAlarmBinding
import com.metehanyl.calarsaat.util.DayUtils
import kotlinx.coroutines.launch

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAlarmBinding
    private val dao by lazy { AlarmDatabase.getInstance(this).alarmDao() }
    private val scheduler by lazy { AlarmScheduler(this) }

    private var editingAlarm: AlarmEntity? = null
    private val dayChips = mutableMapOf<Int, Chip>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        buildDayChips()

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

        val alarm = (editingAlarm ?: AlarmEntity(hour = hour, minute = minute, label = label)).copy(
            hour = hour,
            minute = minute,
            label = label,
            repeatDays = AlarmEntity.daysToString(selectedDays),
            enabled = true
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
