package com.metehanyl.calarsaat.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.metehanyl.calarsaat.data.AlarmEntity
import com.metehanyl.calarsaat.data.AlarmGroupEntity
import com.metehanyl.calarsaat.databinding.ItemAlarmGroupBinding

data class GroupWithAlarms(
    val group: AlarmGroupEntity,
    val alarms: List<AlarmEntity>
)

class AlarmGroupAdapter(
    private val onActivate: (GroupWithAlarms) -> Unit,
    private val onClick: (GroupWithAlarms) -> Unit
) : ListAdapter<GroupWithAlarms, AlarmGroupAdapter.GroupViewHolder>(DIFF) {

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
            binding.textGroupName.text = item.group.name
            binding.textGroupTimes.text = item.alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
                .joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) }
            binding.buttonActivateGroup.setOnClickListener { onActivate(item) }
            binding.root.setOnClickListener { onClick(item) }
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
