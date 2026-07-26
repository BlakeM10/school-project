package com.nextgen.courtvision.ui.livesession

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
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
import com.nextgen.courtvision.BuildConfig
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.databinding.FragmentLiveSessionBinding
import com.nextgen.courtvision.ui.drilllibrary.DrillLibraryFragment
import com.nextgen.courtvision.ui.summary.SessionSummaryFragment
import com.nextgen.courtvision.viewmodel.LiveSessionViewModel
import kotlinx.coroutines.launch

/**
 * Live Session screen: camera viewfinder + metric HUD + recording controls.
 * Phase 6 renders the full screen with a real camera preview; the CV pipeline
 * that feeds detections into the recorder is attached in Phase 7, so live
 * metric counters remain at zero until then.
 */
class LiveSessionFragment : Fragment() {

    private var _binding: FragmentLiveSessionBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as CourtVisionApp).container

    private val viewModel: LiveSessionViewModel by viewModels {
        LiveSessionViewModel.Factory(
            drillId = requireArguments().getString(DrillLibraryFragment.ARG_DRILL_ID).orEmpty(),
            appVersion = BuildConfig.VERSION_NAME,
            authRepository = container.authRepository,
            firestoreRepository = container.firestoreRepository,
        )
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                bindCamera()
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.error_camera_permission,
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLiveSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonStart.setOnClickListener { viewModel.startRecording() }
        binding.buttonFinish.setOnClickListener { viewModel.finishSession() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading || state.saving
                    binding.drillName.text = state.drill?.name.orEmpty()
                    binding.buttonStart.isVisible =
                        state.drill != null && !state.recording && state.savedSessionId == null && !state.saving
                    binding.buttonFinish.isVisible = state.recording

                    state.stats?.let { stats ->
                        binding.hudTimer.text = getString(
                            R.string.hud_timer_format, stats.elapsedSec / 60, stats.elapsedSec % 60,
                        )
                        binding.hudShots.text = getString(
                            R.string.hud_shots_format, stats.shotsMade, stats.shotsAttempted,
                        )
                        binding.hudDribbles.text =
                            getString(R.string.hud_dribbles_format, stats.dribbleCount)
                    }

                    state.savedSessionId?.let { navigateToSummary(it) }

                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.acknowledgeError()
                    }
                }
            }
        }

        if (hasCameraPermission()) {
            bindCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            if (_binding == null) return@addListener
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview,
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun navigateToSummary(sessionId: String) {
        findNavController().navigate(
            R.id.sessionSummaryFragment,
            bundleOf(SessionSummaryFragment.ARG_SESSION_ID to sessionId),
            navOptions { popUpTo(R.id.liveSessionFragment) { inclusive = true } },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
