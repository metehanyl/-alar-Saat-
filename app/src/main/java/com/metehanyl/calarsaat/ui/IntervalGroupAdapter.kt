package com.metehanyl.calarsaat.ui

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.metehanyl.calarsaat.data.IntervalAlarmGroupEntity
import com.metehanyl.calarsaat.databinding.ItemIntervalGroupBinding
import com.metehanyl.calarsaat.util.TimeRemainingFormatter
import java.util.Calendar

class IntervalGroupAdapter(
    private val onToggle: (IntervalAlarmGroupEntity, Boolean) -> Unit,
    private val onClick: (IntervalAlarmGroupEntity) -> Unit
) : ListAdapter<IntervalAlarmGroupEntity, IntervalGroupAdapter.ViewHolder>(DIFF) {

    private val expandedIds = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIntervalGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemIntervalGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: IntervalAlarmGroupEntity) {
            val activeDays = item.repeatDaysSet()
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

            binding.textIntervalGroupName.text = item.name
            binding.textIntervalGroupDetail.text = binding.root.context.getString(
                com.metehanyl.calarsaat.R.string.interval_group_detail,
                "%02d:%02d".format(item.startHour, item.startMinute),
                item.intervalMinutes,
                item.alarmCount
            )

            val endMinutes = item.startHour * 60 + item.startMinute + (item.alarmCount - 1) * item.intervalMinutes
            val endTimeStr = "%02d:%02d".format((endMinutes / 60) % 24, endMinutes % 60)
            val startTimeStr = "%02d:%02d".format(item.startHour, item.startMinute)

            val times = (0 until item.alarmCount).joinToString(", ") { i ->
                val totalMin = item.startHour * 60 + item.startMinute + i * item.intervalMinutes
                "%02d:%02d".format((totalMin / 60) % 24, totalMin % 60)
            }
            binding.textIntervalGroupTimes.text = times

            if (item.enabled) {
                val now = System.currentTimeMillis()
                val repeatDays = item.repeatDaysSet()
                var nextMillis = 0L
                for (i in 0 until item.alarmCount) {
                    val totalMin = item.startHour * 60 + item.startMinute + i * item.intervalMinutes
                    val h = (totalMin / 60) % 24
                    val m = totalMin % 60
                    val candidate = TimeRemainingFormatter.nextTriggerMillis(h, m, repeatDays)
                    if (candidate > now && (nextMillis == 0L || candidate < nextMillis)) {
                        nextMillis = candidate
                    }
                }
                if (nextMillis > now) {
                    val totalMin = ((nextMillis - now) / 60_000L).coerceAtLeast(0)
                    val hours = totalMin / 60
                    val minutes = totalMin % 60
                    binding.textIntervalGroupRemaining.visibility = View.VISIBLE
                    binding.textIntervalGroupRemaining.text = if (hours > 0)
                        binding.root.context.getString(
                            com.metehanyl.calarsaat.R.string.time_remaining_hours_minutes, hours, minutes
                        )
                    else
                        binding.root.context.getString(
                            com.metehanyl.calarsaat.R.string.time_remaining_minutes, minutes
                        )
                } else {
                    binding.textIntervalGroupRemaining.visibility = View.GONE
                }
            } else {
                binding.textIntervalGroupRemaining.visibility = View.GONE
            }

            binding.switchIntervalGroupEnabled.setOnCheckedChangeListener(null)
            binding.switchIntervalGroupEnabled.isChecked = item.enabled
            binding.switchIntervalGroupEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(item, checked)
            }

            val expanded = item.id in expandedIds
            binding.layoutIntervalGroupExpanded.visibility =
                if (expanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonExpandIntervalGroup.rotation = if (expanded) 180f else 0f
            binding.buttonExpandIntervalGroup.setOnClickListener {
                if (expanded) expandedIds.remove(item.id) else expandedIds.add(item.id)
                notifyItemChanged(bindingAdapterPosition)
            }

            binding.layoutIntervalGroupHeader.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<IntervalAlarmGroupEntity>() {
            override fun areItemsTheSame(old: IntervalAlarmGroupEntity, new: IntervalAlarmGroupEntity) =
                old.id == new.id
            override fun areContentsTheSame(old: IntervalAlarmGroupEntity, new: IntervalAlarmGroupEntity) =
                old == new
        }
    }
}
