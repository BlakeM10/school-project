package com.nextgen.courtvision.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.ItemSessionBinding
import com.nextgen.courtvision.domain.model.Session
import java.text.DateFormat
import java.util.Date

class SessionHistoryAdapter(
    private val drillNameFor: (String) -> String,
    private val onDeleteClicked: (Session) -> Unit,
) : ListAdapter<Session, SessionHistoryAdapter.SessionViewHolder>(DiffCallback) {

    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: Session) {
            val context = binding.root.context
            binding.sessionDrillName.text = drillNameFor(session.drillId)
            binding.sessionDate.text = context.getString(
                R.string.session_meta_format,
                dateFormat.format(Date(session.startedAtMillis)),
                session.durationSec / 60,
                session.durationSec % 60,
            )
            binding.sessionStats.text = context.getString(
                R.string.session_stats_short_format,
                session.shotsMade,
                session.shotsAttempted,
            )
            binding.sessionAccuracy.text = "${session.accuracyPct.toInt()}%"
            binding.accuracyBar.progress = session.accuracyPct.toInt()

            binding.performanceBadge.text = context.getString(
                when {
                    session.shotsAttempted == 0 -> R.string.badge_recorded
                    session.accuracyPct >= 70 -> R.string.badge_excellent
                    session.accuracyPct >= 50 -> R.string.badge_solid
                    else -> R.string.badge_building
                },
            )

            // Destructive action lives in an overflow menu, not inline on the card.
            binding.buttonOverflow.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.menu_session_item, menu)
                    setOnMenuItemClickListener { item ->
                        if (item.itemId == R.id.action_delete_session) {
                            onDeleteClicked(session)
                            true
                        } else {
                            false
                        }
                    }
                }.show()
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Session, newItem: Session) = oldItem == newItem
    }
}
