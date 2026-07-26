package com.nextgen.courtvision.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
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
            binding.sessionDate.text = dateFormat.format(Date(session.startedAtMillis))
            binding.sessionStats.text = context.getString(
                R.string.session_stats_format,
                session.shotsMade,
                session.shotsAttempted,
                session.accuracyPct.toInt(),
            )
            binding.buttonDelete.setOnClickListener { onDeleteClicked(session) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Session, newItem: Session) = oldItem == newItem
    }
}
