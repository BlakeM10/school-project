package com.nextgen.courtvision.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextgen.courtvision.databinding.ItemPlayerBinding
import com.nextgen.courtvision.domain.model.Player

class PlayerAdapter(
    private val onPlayerClicked: (Player) -> Unit,
) : ListAdapter<PlayerAdapter.Row, PlayerAdapter.PlayerViewHolder>(DiffCallback) {

    data class Row(val player: Player, val selected: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlayerViewHolder(
        private val binding: ItemPlayerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row) {
            binding.playerName.text = row.player.displayName
            binding.root.isChecked = row.selected
            binding.root.setOnClickListener { onPlayerClicked(row.player) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row) =
            oldItem.player.uid == newItem.player.uid

        override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
    }
}
