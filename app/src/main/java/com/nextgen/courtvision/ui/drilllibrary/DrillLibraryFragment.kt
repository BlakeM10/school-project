package com.nextgen.courtvision.ui.drilllibrary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentDrillLibraryBinding
import com.nextgen.courtvision.viewmodel.AuthViewModel
import com.nextgen.courtvision.viewmodel.DrillLibraryViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Player dashboard: welcome header, training stats, insights, drill catalogue. */
class DrillLibraryFragment : Fragment() {

    private var _binding: FragmentDrillLibraryBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as CourtVisionApp).container

    private val authViewModel: AuthViewModel by activityViewModels {
        AuthViewModel.Factory(container.authRepository)
    }

    private val viewModel: DrillLibraryViewModel by viewModels {
        DrillLibraryViewModel.Factory(container.authRepository, container.firestoreRepository)
    }

    private lateinit var drillAdapter: DrillAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDrillLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        drillAdapter = DrillAdapter { drill ->
            findNavController().navigate(
                R.id.liveSessionFragment,
                bundleOf(ARG_DRILL_ID to drill.id),
            )
        }
        binding.drillList.adapter = drillAdapter

        binding.buttonJoinTeam.setOnClickListener { showJoinTeamDialog() }
        binding.buttonProgress.setOnClickListener {
            findNavController().navigate(R.id.playerProgressFragment)
        }
        binding.buttonSignOut.setOnClickListener { authViewModel.signOut() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading || state.joiningTeam
                    binding.teamBanner.isVisible = state.needsTeam
                    binding.drillList.isVisible = state.drills.isNotEmpty()
                    binding.emptyState.isVisible =
                        !state.loading && state.drills.isEmpty() && state.error == null
                    drillAdapter.submitList(state.drills)

                    val name = state.user?.displayName.orEmpty()
                    binding.welcomeName.text = name.ifBlank { getString(R.string.role_player) }
                    binding.avatarText.text =
                        name.trim().firstOrNull()?.uppercase() ?: "•"

                    binding.todayStatus.text = getString(
                        if (state.stats.trainedToday) R.string.home_today_done
                        else R.string.home_today_pending,
                    )
                    binding.statStreak.bind(
                        value = state.stats.streakDays.toString(),
                        label = getString(R.string.stat_streak_label),
                    )
                    binding.statSessions.bind(
                        value = state.stats.sessionCount.toString(),
                        label = getString(R.string.stat_sessions_label),
                    )
                    binding.statAccuracy.bind(
                        value = "${state.stats.avgAccuracyPct.roundToInt()}%",
                        label = getString(R.string.stat_accuracy_label),
                        trend = trendText(state.stats.accuracyDeltaPct),
                    )
                    binding.insightsText.text =
                        state.insights.joinToString("\n\n") { "•  $it" }

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

    private fun showJoinTeamDialog() {
        val inputLayout = TextInputLayout(requireContext()).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            hint = getString(R.string.hint_team_code)
        }
        val input = TextInputEditText(inputLayout.context)
        inputLayout.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.join_team_title)
            .setMessage(R.string.join_team_message)
            .setView(inputLayout)
            .setPositiveButton(R.string.action_join) { _, _ ->
                viewModel.joinTeam(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_DRILL_ID = "drillId"
    }
}
