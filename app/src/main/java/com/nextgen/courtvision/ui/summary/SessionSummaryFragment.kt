package com.nextgen.courtvision.ui.summary

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
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentSessionSummaryBinding
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.viewmodel.SessionSummaryViewModel
import kotlinx.coroutines.launch

/** Post-drill summary: radial accuracy chart, shot distribution, metric rows. */
class SessionSummaryFragment : Fragment() {

    private var _binding: FragmentSessionSummaryBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as CourtVisionApp).container

    private val viewModel: SessionSummaryViewModel by viewModels {
        SessionSummaryViewModel.Factory(
            sessionId = requireArguments().getString(ARG_SESSION_ID).orEmpty(),
            firestoreRepository = container.firestoreRepository,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSessionSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonDone.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.content.isVisible = state.session != null
                    state.session?.let { render(it, state.drill?.name) }
                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun render(session: Session, drillName: String?) {
        binding.drillName.text = drillName ?: session.drillId
        binding.radialChart.progressPct = session.accuracyPct.toFloat()

        renderShotChart(session)

        binding.rowShots.text = getString(
            R.string.summary_shots_format, session.shotsMade, session.shotsAttempted,
        )
        binding.rowReleaseTime.text = if (session.avgReleaseTimeMs > 0) {
            getString(R.string.summary_release_format, session.avgReleaseTimeMs.toInt())
        } else {
            getString(R.string.summary_not_measured)
        }
        binding.rowDribbles.text = if (session.dribbleCount > 0) {
            getString(
                R.string.summary_dribbles_format, session.dribbleCount, session.dribbleSpeedHz,
            )
        } else {
            getString(R.string.summary_not_measured)
        }
        binding.rowReaction.text = if (session.avgReactionTimeMs > 0) {
            getString(R.string.summary_reaction_format, session.avgReactionTimeMs.toInt())
        } else {
            getString(R.string.summary_not_measured)
        }
        binding.rowDuration.text = getString(
            R.string.drill_duration_format, session.durationSec / 60, session.durationSec % 60,
        )
    }

    private fun renderShotChart(session: Session) {
        val missed = session.shotsAttempted - session.shotsMade
        val entries = listOf(
            BarEntry(0f, session.shotsMade.toFloat()),
            BarEntry(1f, missed.toFloat()),
        )
        val dataSet = BarDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(requireContext(), R.color.court_orange),
                ContextCompat.getColor(requireContext(), R.color.court_navy),
            )
            valueTextSize = 12f
        }
        binding.shotChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.valueFormatter = IndexAxisValueFormatter(
                listOf(getString(R.string.chart_made), getString(R.string.chart_missed)),
            )
            setTouchEnabled(false)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
