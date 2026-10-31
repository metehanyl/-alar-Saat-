package com.metehanyl.calarsaat.ui

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.metehanyl.calarsaat.R
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.databinding.ItemAlarmBinding
import com.metehanyl.calarsaat.util.DayUtils
import com.metehanyl.calarsaat.util.TimeRemainingFormatter
import java.util.Calendar

class AlarmAdapter(
    private val onToggle: (AlarmEntity, Boolean) -> Unit,
    private val onClick: (AlarmEntity) -> Unit
) : ListAdapter<AlarmEntity, AlarmAdapter.AlarmViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: AlarmEntity) {
            binding.textTime.text = "%02d:%02d".format(alarm.hour, alarm.minute)
            val repeatSummary = DayUtils.summarize(alarm.repeatDaysSet())

            if (alarm.isAutoSabahNamazi) {
                // Sabah alarms: keep classic label + day summary, no day chips
                binding.textLabel.text = if (alarm.label.isBlank()) repeatSummary
                else "${alarm.label} • $repeatSummary"
                binding.layoutDays.visibility = View.GONE
            } else {
                // Regular alarms: show just the label, then colored day chips
                binding.textLabel.text = alarm.label.ifBlank { "" }
                binding.layoutDays.visibility = View.VISIBLE
                val activeDays = alarm.repeatDaysSet()
                val dayViews = listOf(
                    Calendar.MONDAY to binding.textDayMon,
                    Calendar.TUESDAY to binding.textDayTue,
                    Calendar.WEDNESDAY to binding.textDayWed,
                    Calendar.THURSDAY to binding.textDayThu,
                    Calendar.FRIDAY to binding.textDayFri,
                    Calendar.SATURDAY to binding.textDaySat,
                    Calendar.SUNDAY to binding.textDaySun
                )
                for ((dayValue, tv) in dayViews) {
                    if (activeDays.isEmpty() || dayValue in activeDays) {
                        tv.setTextColor(Color.parseColor("#4CAF50"))
                        tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    } else {
                        tv.setTextColor(Color.parseColor("#F44336"))
                        tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    }
                }
            }

            if (alarm.enabled) {
                binding.textRemaining.visibility = View.VISIBLE
                binding.textRemaining.text = TimeRemainingFormatter.format(
                    binding.root.context, alarm.hour, alarm.minute, alarm.repeatDaysSet()
                )
            } else {
                binding.textRemaining.visibility = View.GONE
            }

            if (alarm.isSnoozed && alarm.snoozedUntilMillis > 0) {
                val cal = Calendar.getInstance().apply { timeInMillis = alarm.snoozedUntilMillis }
                val timeStr = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                binding.textSnoozeInfo.text = binding.root.context.getString(R.string.alarm_snoozed_until, timeStr)
                binding.textSnoozeInfo.visibility = View.VISIBLE
            } else {
                binding.textSnoozeInfo.visibility = View.GONE
            }

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = alarm.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(alarm, checked)
            }

            binding.root.setOnClickListener { onClick(alarm) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AlarmEntity>() {
            override fun areItemsTheSame(oldItem: AlarmEntity, newItem: AlarmEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AlarmEntity, newItem: AlarmEntity) =
                oldItem == newItem
        }
    }
}
