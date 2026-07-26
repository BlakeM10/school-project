package com.nextgen.courtvision.ui.drilllibrary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.ItemDrillBinding
import com.nextgen.courtvision.domain.model.Drill

class DrillAdapter(
    private val onDrillClicked: (Drill) -> Unit,
) : ListAdapter<Drill, DrillAdapter.DrillViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrillViewHolder {
        val binding = ItemDrillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DrillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DrillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DrillViewHolder(
        private val binding: ItemDrillBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(drill: Drill) {
            binding.drillName.text = drill.name
            binding.drillCategory.text = drill.category.wireName
                .replaceFirstChar { it.uppercase() }
            binding.drillDuration.text = binding.root.context.getString(
                R.string.drill_duration_format, drill.durationSec / 60, drill.durationSec % 60,
            )
            binding.drillInstructions.text = drill.instructions
            binding.root.setOnClickListener { onDrillClicked(drill) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Drill>() {
        override fun areItemsTheSame(oldItem: Drill, newItem: Drill) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Drill, newItem: Drill) = oldItem == newItem
    }
}
