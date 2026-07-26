package com.nextgen.courtvision.ui.livesession

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import com.nextgen.courtvision.cv.CameraCVPipeline
import com.nextgen.courtvision.cv.FeedbackEngine
import com.nextgen.courtvision.databinding.FragmentLiveSessionBinding
import com.nextgen.courtvision.domain.cv.BallDetection
import com.nextgen.courtvision.domain.cv.CVPipelineListener
import com.nextgen.courtvision.domain.cv.PoseFrame
import com.nextgen.courtvision.domain.cv.ShotEvent
import com.nextgen.courtvision.ui.drilllibrary.DrillLibraryFragment
import com.nextgen.courtvision.ui.summary.SessionSummaryFragment
import com.nextgen.courtvision.viewmodel.LiveSessionViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Live Session screen: camera viewfinder, green skeleton + ball box overlay,
 * metric HUD, audio feedback, and the recording lifecycle. The CV pipeline
 * (BlazePose + TFLite ball detection) analyses frames and its events drive the
 * SessionRecorder through the ViewModel.
 */
class LiveSessionFragment : Fragment(), CVPipelineListener {

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

    private var cvPipeline: CameraCVPipeline? = null
    private var feedbackEngine: FeedbackEngine? = null
    private var analysisExecutor: ExecutorService? = null
    private var latestPose: PoseFrame? = null
    private var latestBall: BallDetection? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                bindCameraWhenReady()
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

        feedbackEngine = FeedbackEngine()

        binding.buttonStart.setOnClickListener {
            viewModel.startRecording()
            cvPipeline?.setRecording(true)
        }
        binding.buttonFinish.setOnClickListener {
            cvPipeline?.setRecording(false)
            viewModel.finishSession()
        }

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
            bindCameraWhenReady()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** Camera + pipeline need the drill's measures, so wait for the drill load. */
    private fun bindCameraWhenReady() {
        viewLifecycleOwner.lifecycleScope.launch {
            val drill = viewModel.uiState.first { it.drill != null }.drill ?: return@launch
            if (cvPipeline == null) setUpPipelineAndCamera(drill.measures)
        }
    }

    private fun setUpPipelineAndCamera(measures: List<com.nextgen.courtvision.domain.model.Measure>) {
        val pipeline = CameraCVPipeline(requireContext().applicationContext, measures, this)
        pipeline.onReactionCue = { feedbackEngine?.playReactionCue() }
        cvPipeline = pipeline

        binding.modelsWarning.isVisible = !pipeline.isFullyAvailable

        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            if (_binding == null) return@addListener
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, pipeline) }

            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // CVPipelineListener — all callbacks arrive on the main thread.

    override fun onPoseFrame(frame: PoseFrame) {
        latestPose = frame
        _binding?.poseOverlay?.update(latestPose, latestBall)
    }

    override fun onBallDetected(ball: BallDetection) {
        latestBall = ball
        _binding?.poseOverlay?.update(latestPose, latestBall)
    }

    override fun onShotDetected(event: ShotEvent) {
        feedbackEngine?.playShotFeedback(event.made)
        viewModel.onShotDetected(event.made, event.releaseTimeMs)
    }

    override fun onDribbleDetected(intervalMs: Long) {
        viewModel.onDribbleDetected(intervalMs)
    }

    override fun onReactionMeasured(reactionMs: Long) {
        viewModel.onReactionMeasured(reactionMs)
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun navigateToSummary(sessionId: String) {
        findNavController().navigate(
            R.id.sessionSummaryFragment,
            bundleOf(SessionSummaryFragment.ARG_SESSION_ID to sessionId),
            navOptions { popUpTo(R.id.liveSessionFragment) { inclusive = true } },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cvPipeline?.setRecording(false)
        cvPipeline?.close()
        cvPipeline = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        feedbackEngine?.release()
        feedbackEngine = null
        _binding = null
    }
}
