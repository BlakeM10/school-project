package com.nextgen.courtvision.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.nextgen.courtvision.domain.cv.BallDetection
import com.nextgen.courtvision.domain.cv.CVPipelineListener
import com.nextgen.courtvision.domain.cv.DribbleDetector
import com.nextgen.courtvision.domain.cv.PoseFrame
import com.nextgen.courtvision.domain.cv.ReactionTimer
import com.nextgen.courtvision.domain.cv.ShotDetector
import com.nextgen.courtvision.domain.model.Measure
import kotlin.random.Random

/**
 * The CVPipeline from the proposal's class model: consumes CameraX analysis
 * frames, runs pose estimation + ball detection, feeds the pure detection
 * algorithms, and emits events through CVPipelineListener. Everything inside
 * (models, frame handling, detector state) is hidden behind that interface so
 * the detection internals can change without touching any other class.
 */
class CameraCVPipeline(
    context: Context,
    private val measures: List<Measure>,
    private val listener: CVPipelineListener,
) : ImageAnalysis.Analyzer {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val shotDetector = ShotDetector { event ->
        mainHandler.post { listener.onShotDetected(event) }
    }
    private val dribbleDetector = DribbleDetector { intervalMs ->
        mainHandler.post { listener.onDribbleDetected(intervalMs) }
    }
    private val reactionTimer = ReactionTimer { reactionMs ->
        mainHandler.post { listener.onReactionMeasured(reactionMs) }
    }

    @Volatile
    private var latestBall: BallDetection? = null

    @Volatile
    private var recording = false

    private val ballDetector = BallDetectorEngine(context)
    private val poseEstimator = PoseEstimator(context) { pose -> onPoseResult(pose) }

    val isFullyAvailable: Boolean
        get() = poseEstimator.isAvailable && ballDetector.isAvailable

    /** Gates event detection; preview overlays stay live regardless. */
    fun setRecording(active: Boolean) {
        recording = active
        if (active) {
            shotDetector.reset()
            dribbleDetector.reset()
            reactionTimer.disarm()
            if (Measure.REACTION_TIME in measures) scheduleNextReactionCue()
        } else {
            mainHandler.removeCallbacksAndMessages(CUE_TOKEN)
            reactionTimer.disarm()
        }
    }

    /** Reaction cues need the caller to actually play the sound. */
    var onReactionCue: (() -> Unit)? = null

    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.use { proxy ->
            val timestampMs = SystemClock.uptimeMillis()
            val bitmap = proxy.toRotatedBitmap()

            if (ballDetector.isAvailable) {
                val ball = ballDetector.detect(bitmap, timestampMs)
                if (ball != null) {
                    latestBall = ball
                    mainHandler.post { listener.onBallDetected(ball) }
                }
            }

            if (poseEstimator.isAvailable) {
                poseEstimator.detectAsync(bitmap, timestampMs)
            } else if (recording) {
                // No pose model: still drive ball-only detectors so dribble
                // counting keeps some capability (hip gate absent → skipped).
                shotDetector.onFrame(null, latestBall, timestampMs)
            }
        }
    }

    private fun onPoseResult(pose: PoseFrame) {
        mainHandler.post { listener.onPoseFrame(pose) }
        if (!recording) return

        val ball = latestBall
        if (Measure.SHOTS in measures || Measure.RELEASE_TIME in measures) {
            shotDetector.onFrame(pose, ball, pose.timestampMs)
        }
        if (Measure.DRIBBLES in measures) {
            dribbleDetector.onFrame(pose, ball)
        }
        if (Measure.REACTION_TIME in measures) {
            reactionTimer.onFrame(pose)
        }
    }

    private fun scheduleNextReactionCue() {
        val delayMs = Random.nextLong(CUE_MIN_DELAY_MS, CUE_MAX_DELAY_MS)
        mainHandler.postDelayed(
            {
                if (recording) {
                    onReactionCue?.invoke()
                    reactionTimer.arm(SystemClock.uptimeMillis())
                    scheduleNextReactionCue()
                }
            },
            CUE_TOKEN,
            delayMs,
        )
    }

    fun close() {
        mainHandler.removeCallbacksAndMessages(CUE_TOKEN)
        poseEstimator.close()
        ballDetector.close()
    }

    private fun ImageProxy.toRotatedBitmap(): Bitmap {
        val bitmap = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        val CUE_TOKEN = Any()
        const val CUE_MIN_DELAY_MS = 4_000L
        const val CUE_MAX_DELAY_MS = 9_000L
    }
}
