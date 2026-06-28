package com.metehanyl.calarsaat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.databinding.ItemAlarmBinding
import com.metehanyl.calarsaat.util.DayUtils
import com.metehanyl.calarsaat.util.TimeRemainingFormatter

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
            binding.textLabel.text = if (alarm.label.isBlank()) repeatSummary
            else "${alarm.label} • $repeatSummary"

            if (alarm.enabled) {
                binding.textRemaining.visibility = View.VISIBLE
                binding.textRemaining.text = TimeRemainingFormatter.format(
                    binding.root.context, alarm.hour, alarm.minute, alarm.repeatDaysSet()
                )
            } else {
                binding.textRemaining.visibility = View.GONE
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
