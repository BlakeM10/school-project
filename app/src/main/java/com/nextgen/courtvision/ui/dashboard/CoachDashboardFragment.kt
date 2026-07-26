package com.nextgen.courtvision.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.data.Entry
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentCoachDashboardBinding
import com.nextgen.courtvision.databinding.ItemPerformerBinding
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.stats.PlayerSummary
import com.nextgen.courtvision.ui.common.ChartStyler
import com.nextgen.courtvision.viewmodel.AuthViewModel
import com.nextgen.courtvision.viewmodel.CoachDashboardViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Coach analytics dashboard: team overview tiles, ranked performers,
 * attention flags, data-driven recommendations, roster, and per-player trends.
 */
class CoachDashboardFragment : Fragment() {

    private var _binding: FragmentCoachDashboardBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as CourtVisionApp).container

    private val authViewModel: AuthViewModel by activityViewModels {
        AuthViewModel.Factory(container.authRepository)
    }

    private val viewModel: CoachDashboardViewModel by viewModels {
        CoachDashboardViewModel.Factory(container.authRepository, container.firestoreRepository)
    }

    private lateinit var playerAdapter: PlayerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCoachDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerAdapter = PlayerAdapter { viewModel.selectPlayer(it) }
        binding.playerList.adapter = playerAdapter

        binding.buttonCreateTeam.setOnClickListener {
            viewModel.createTeam(binding.inputTeamName.text?.toString().orEmpty())
        }
        binding.buttonSignOut.setOnClickListener { authViewModel.signOut() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading || state.creatingTeam
                    binding.createTeamGroup.isVisible = state.needsTeam && !state.loading
                    binding.dashboardGroup.isVisible = state.team != null

                    binding.teamName.text = state.team?.name.orEmpty()
                    state.team?.let { binding.joinCode.text = it.joinCode }

                    binding.statTeamAccuracy.bind(
                        "${state.teamAvgAccuracyPct.roundToInt()}%",
                        getString(R.string.stat_team_accuracy_label),
                    )
                    binding.statPlayers.bind(
                        state.players.size.toString(),
                        getString(R.string.stat_players_label),
                    )
                    binding.statImproving.bind(
                        state.improvingCount.toString(),
                        getString(R.string.stat_improving_label),
                    )
                    binding.statAttention.bind(
                        state.attentionCount.toString(),
                        getString(R.string.stat_attention_label),
                    )

                    binding.recommendationsText.text =
                        state.recommendations.joinToString("\n\n") { "•  $it" }

                    renderSummaryRows(binding.topPerformers, state.topPerformers)
                    binding.attentionTitle.isVisible = state.needsAttention.isNotEmpty()
                    renderSummaryRows(binding.attentionList, state.needsAttention)

                    playerAdapter.submitList(
                        state.players.map {
                            PlayerAdapter.Row(it, it.uid == state.selectedPlayer?.uid)
                        },
                    )
                    binding.emptyRoster.isVisible =
                        state.team != null && state.players.isEmpty()

                    binding.selectedPlayerName.isVisible = state.selectedPlayer != null
                    binding.selectedPlayerName.text = state.selectedPlayer?.let {
                        getString(R.string.dashboard_trend_title, it.displayName)
                    }
                    renderTrendChart(state.selectedPlayerSessions)

                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.acknowledgeError()
                    }
                }
            }
        }
    }

    private fun renderSummaryRows(container: LinearLayout, summaries: List<PlayerSummary>) {
        container.removeAllViews()
        summaries.forEachIndexed { index, summary ->
            val row = ItemPerformerBinding.inflate(layoutInflater, container, false)
            row.performerRank.text = (index + 1).toString()
            row.performerName.text = summary.player.displayName
            row.performerAccuracy.text = "${summary.avgAccuracyPct.roundToInt()}%"
            val delta = summary.deltaPct.roundToInt()
            when {
                delta >= 1 -> {
                    row.performerTrend.text = getString(R.string.trend_up_format, delta)
                    row.performerTrend.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.cv_success),
                    )
                }
                delta <= -1 -> {
                    row.performerTrend.text = getString(R.string.trend_down_format, abs(delta))
                    row.performerTrend.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.cv_error),
                    )
                }
                else -> row.performerTrend.text = ""
            }
            container.addView(row.root)
        }
    }

    private fun renderTrendChart(sessions: List<Session>) {
        val chronological = sessions.sortedBy { it.startedAtMillis }
        binding.trendChart.isVisible = chronological.isNotEmpty()
        binding.emptyTrend.isVisible = chronological.isEmpty()
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
