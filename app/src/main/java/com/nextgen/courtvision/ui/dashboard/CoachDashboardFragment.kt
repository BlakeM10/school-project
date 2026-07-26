package com.nextgen.courtvision.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentCoachDashboardBinding
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.viewmodel.AuthViewModel
import com.nextgen.courtvision.viewmodel.CoachDashboardViewModel
import kotlinx.coroutines.launch

/**
 * Coach home screen: team creation with a shareable join code, live player
 * roster, and the longitudinal accuracy trend chart for the selected player.
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

                    state.team?.let { team ->
                        binding.teamName.text = team.name
                        binding.joinCode.text = team.joinCode
                    }

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

    private fun renderTrendChart(sessions: List<Session>) {
        // observeSessions returns newest-first; the trend chart reads oldest → newest.
        val chronological = sessions.sortedBy { it.startedAtMillis }
        binding.trendChart.isVisible = chronological.isNotEmpty()
        binding.emptyTrend.isVisible = chronological.isEmpty()
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
