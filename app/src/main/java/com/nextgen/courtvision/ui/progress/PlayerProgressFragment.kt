package com.nextgen.courtvision.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentPlayerProgressBinding
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.viewmodel.PlayerProgressViewModel
import kotlinx.coroutines.launch

/** Player-facing longitudinal progress report: accuracy trend + session history. */
class PlayerProgressFragment : Fragment() {

    private var _binding: FragmentPlayerProgressBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as CourtVisionApp).container

    private val viewModel: PlayerProgressViewModel by viewModels {
        PlayerProgressViewModel.Factory(container.authRepository, container.firestoreRepository)
    }

    private lateinit var historyAdapter: SessionHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlayerProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyAdapter = SessionHistoryAdapter(
            drillNameFor = { drillId ->
                viewModel.uiState.value.drillsById[drillId]?.name ?: drillId
            },
            onDeleteClicked = { session -> confirmDelete(session) },
        )
        binding.historyList.adapter = historyAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.emptyState.isVisible = !state.loading && state.sessions.isEmpty()
                    historyAdapter.submitList(state.sessions)
                    renderTrendChart(state.sessions)
                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.acknowledgeError()
                    }
                }
            }
        }
    }

    private fun confirmDelete(session: Session) {
        // Deletion is the player's legal right (right to erasure) but must not
        // happen from an accidental tap on an analytics record.
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_session_title)
            .setMessage(R.string.delete_session_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteSession(session.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderTrendChart(sessions: List<Session>) {
        val chronological = sessions.sortedBy { it.startedAtMillis }
        binding.trendChart.isVisible = chronological.isNotEmpty()
        if (chronological.isEmpty()) return

        val entries = chronological.mapIndexed { index, session ->
            Entry(index.toFloat(), session.accuracyPct.toFloat())
        }
        val dataSet = LineDataSet(entries, getString(R.string.chart_accuracy_label)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.court_orange)
            setCircleColor(color)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
        }
        binding.trendChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 100f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            setTouchEnabled(false)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
