package com.metehanyl.calarsaat.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.AlarmGroupEntity
import com.metehanyl.calarsaat.databinding.ItemAlarmGroupBinding
import com.metehanyl.calarsaat.util.DayUtils

data class GroupWithAlarms(
    val group: AlarmGroupEntity,
    val alarms: List<AlarmEntity>
)

class AlarmGroupAdapter(
    private val onToggle: (GroupWithAlarms, Boolean) -> Unit,
    private val onClick: (GroupWithAlarms) -> Unit
) : ListAdapter<GroupWithAlarms, AlarmGroupAdapter.GroupViewHolder>(DIFF) {

    private val expandedGroupIds = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): GroupViewHolder {
        val binding = ItemAlarmGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(private val binding: ItemAlarmGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GroupWithAlarms) {
            val repeatSummary = DayUtils.summarize(item.alarms.firstOrNull()?.repeatDaysSet() ?: emptySet())
            binding.textGroupName.text = "${item.group.name} • $repeatSummary"
            binding.textGroupTimes.text = item.alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
                .joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) }

            binding.switchGroupEnabled.setOnCheckedChangeListener(null)
            binding.switchGroupEnabled.isChecked = item.alarms.isNotEmpty() && item.alarms.all { it.enabled }
            binding.switchGroupEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(item, checked)
            }

            val expanded = item.group.id in expandedGroupIds
            binding.layoutGroupTimesExpanded.visibility =
                if (expanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonExpandGroup.rotation = if (expanded) 180f else 0f
            binding.buttonExpandGroup.setOnClickListener {
                if (expanded) expandedGroupIds.remove(item.group.id) else expandedGroupIds.add(item.group.id)
                notifyItemChanged(bindingAdapterPosition)
            }

            binding.layoutGroupHeader.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GroupWithAlarms>() {
            override fun areItemsTheSame(oldItem: GroupWithAlarms, newItem: GroupWithAlarms) =
                oldItem.group.id == newItem.group.id

            override fun areContentsTheSame(oldItem: GroupWithAlarms, newItem: GroupWithAlarms) =
                oldItem == newItem
        }
    }
}
