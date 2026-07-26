package com.nextgen.courtvision.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.data.Entry
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentPlayerProgressBinding
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.stats.StatsPeriod
import com.nextgen.courtvision.ui.common.ChartStyler
import com.nextgen.courtvision.viewmodel.PlayerProgressViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Premium analytics: period filters, summary tiles, gradient trend chart, history. */
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

        binding.periodChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val period = when (checkedIds.firstOrNull()) {
                R.id.chipWeek -> StatsPeriod.WEEK
                R.id.chipYear -> StatsPeriod.YEAR
                R.id.chipAll -> StatsPeriod.ALL
                else -> StatsPeriod.MONTH
            }
            viewModel.setPeriod(period)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.emptyState.isVisible = !state.loading && state.sessions.isEmpty()
                    historyAdapter.submitList(state.sessions)

                    binding.statAvg.bind(
                        "${state.stats.avgAccuracyPct.roundToInt()}%",
                        getString(R.string.stat_avg_label),
                        trendText(state.stats.accuracyDeltaPct),
                    )
                    binding.statBest.bind(
                        "${state.stats.bestAccuracyPct.roundToInt()}%",
                        getString(R.string.stat_best_label),
                    )
                    binding.statShots.bind(
                        state.stats.totalShots.toString(),
                        getString(R.string.stat_shots_label),
                    )
                    binding.statSessions.bind(
                        state.stats.sessionCount.toString(),
                        getString(R.string.stat_sessions_label),
                    )
                    binding.statStreak.bind(
                        state.stats.streakDays.toString(),
                        getString(R.string.stat_streak_label),
                    )
                    binding.statPersonalBest.bind(
                        state.stats.personalBest?.let { "${it.accuracyPct.roundToInt()}%" } ?: "—",
                        getString(R.string.stat_pb_label),
                    )

                    renderTrendChart(state.sessions)

                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.acknowledgeError()
                    }
                }
            }
        }
    }

    private fun trendText(deltaPct: Double): String? = when {
        deltaPct >= 1 -> getString(R.string.trend_up_format, deltaPct.roundToInt())
        deltaPct <= -1 -> getString(R.string.trend_down_format, -deltaPct.roundToInt())
        else -> null
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
        binding.chartCard.isVisible = chronological.isNotEmpty()
        if (chronological.isEmpty()) return

        val entries = chronological.mapIndexed { index, session ->
            Entry(index.toFloat(), session.accuracyPct.toFloat())
        }
        ChartStyler.applyAccuracyLine(
            binding.trendChart, entries, getString(R.string.chart_accuracy_label),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
