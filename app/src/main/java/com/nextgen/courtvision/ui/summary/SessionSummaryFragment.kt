package com.nextgen.courtvision.ui.summary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.snackbar.Snackbar
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentSessionSummaryBinding
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.ui.common.ChartStyler
import com.nextgen.courtvision.ui.drilllibrary.DrillLibraryFragment
import com.nextgen.courtvision.viewmodel.SessionSummaryViewModel
import kotlinx.coroutines.launch

/** Rewarding post-drill screen: animated ring, stat tiles, feedback, share. */
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

    private var ringAnimated = false

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
                    state.session?.let { session ->
                        render(session, state.drill?.name, state.isPersonalBest, state.feedback)
                    }
                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun render(
        session: Session,
        drillName: String?,
        isPersonalBest: Boolean,
        feedback: List<String>,
    ) {
        binding.drillName.text = drillName ?: session.drillId
        binding.personalBestBadge.isVisible = isPersonalBest

        if (!ringAnimated) {
            ringAnimated = true
            binding.radialChart.setProgressAnimated(session.accuracyPct.toFloat())
        } else {
            binding.radialChart.progressPct = session.accuracyPct.toFloat()
        }

        binding.statMade.bind(session.shotsMade.toString(), getString(R.string.stat_made_label))
        binding.statAttempted.bind(
            session.shotsAttempted.toString(), getString(R.string.stat_attempted_label),
        )
        binding.statDuration.bind(
            getString(R.string.drill_duration_format, session.durationSec / 60, session.durationSec % 60),
            getString(R.string.stat_duration_label),
        )

        binding.feedbackText.text = feedback.joinToString("\n\n") { "•  $it" }

        ChartStyler.applyShotDistribution(
            binding.shotChart,
            made = session.shotsMade,
            missed = session.shotsAttempted - session.shotsMade,
            madeLabel = getString(R.string.chart_made),
            missedLabel = getString(R.string.chart_missed),
        )

        binding.rowShots.text = getString(
            R.string.summary_shots_format, session.shotsMade, session.shotsAttempted,
        )
        binding.rowReleaseTime.text = if (session.avgReleaseTimeMs > 0) {
            getString(R.string.summary_release_format, session.avgReleaseTimeMs.toInt())
        } else {
            getString(R.string.summary_not_measured)
        }
        binding.rowDribbles.text = if (session.dribbleCount > 0) {
            getString(R.string.summary_dribbles_format, session.dribbleCount, session.dribbleSpeedHz)
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

        binding.buttonTrainAgain.setOnClickListener {
            findNavController().navigate(
                R.id.liveSessionFragment,
                bundleOf(DrillLibraryFragment.ARG_DRILL_ID to session.drillId),
                navOptions { popUpTo(R.id.sessionSummaryFragment) { inclusive = true } },
            )
        }
        binding.buttonShare.setOnClickListener { shareSession(session, drillName) }
    }

    private fun shareSession(session: Session, drillName: String?) {
        val text = getString(
            R.string.share_session_format,
            drillName ?: session.drillId,
            session.shotsMade,
            session.shotsAttempted,
            session.accuracyPct.toInt(),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
