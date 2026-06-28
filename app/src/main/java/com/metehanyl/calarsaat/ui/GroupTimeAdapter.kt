package com.metehanyl.calarsaat.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.metehanyl.calarsaat.databinding.ItemGroupTimeBinding
import com.metehanyl.calarsaat.util.TimeRemainingFormatter

class GroupTimeAdapter(
    private val times: MutableList<Pair<Int, Int>>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<GroupTimeAdapter.TimeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): TimeViewHolder {
        val binding = ItemGroupTimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        holder.bind(times[position])
    }

    override fun getItemCount(): Int = times.size

    inner class TimeViewHolder(private val binding: ItemGroupTimeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(time: Pair<Int, Int>) {
            binding.textTime.text = "%02d:%02d".format(time.first, time.second)
            binding.textRemaining.text =
                TimeRemainingFormatter.format(binding.root.context, time.first, time.second)
            binding.buttonDeleteTime.setOnClickListener { onDelete(bindingAdapterPosition) }
        }
    }
}
